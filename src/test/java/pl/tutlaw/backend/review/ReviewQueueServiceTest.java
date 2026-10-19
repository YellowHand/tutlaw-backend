package pl.tutlaw.backend.review;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import pl.tutlaw.backend.flashcard.FlashcardAdditionRepository;
import pl.tutlaw.backend.flashcard.FlashcardUnlockService;
import pl.tutlaw.backend.pool.Pool;
import pl.tutlaw.backend.pool.SourceFilter;
import pl.tutlaw.backend.question.Question;
import pl.tutlaw.backend.question.QuestionRepository;
import pl.tutlaw.backend.review.dto.BacklogResponse;
import pl.tutlaw.backend.review.dto.FlashcardQueueItemResponse;
import pl.tutlaw.backend.review.dto.TestQueueItemResponse;
import pl.tutlaw.backend.review.fsrs.FsrsEngine;
import pl.tutlaw.backend.review.fsrs.FsrsWeights;
import pl.tutlaw.backend.settings.ExamTermRepository;
import pl.tutlaw.backend.settings.LearningDefaults;
import pl.tutlaw.backend.settings.LearningSettingsService;
import pl.tutlaw.backend.settings.UserSettings;
import pl.tutlaw.backend.settings.UserSettingsRepository;
import pl.tutlaw.backend.testsupport.FakeRepos;
import pl.tutlaw.backend.testsupport.MutableClock;
import pl.tutlaw.backend.testsupport.Pools;
import pl.tutlaw.backend.testsupport.QuestionRefs;
import pl.tutlaw.backend.track.TrackRepository;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/** Układanie kolejek na prawdziwym ReviewQueueService, z repozytoriami "w pamięci". */
class ReviewQueueServiceTest {

    /** 2026-10-12 08:00 UTC to 10:00 w Warszawie. */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 10, 0);

    private static final class Fixture {
        final List<Question> questions = new ArrayList<>();
        final List<UserQuestionStateRepository.QuestionDue> testSchedule = new ArrayList<>();
        final List<UserQuestionStateRepository.QuestionDue> flashcardSchedule = new ArrayList<>();
        /** Numery pytań, o których pełne treści poproszono bazę (powinny to być tylko pytania z sesji). */
        final List<Long> loadedQuestionIds = new ArrayList<>();
        /** Pytania testowe, które użytkownik pomylił bez „Nie jestem pewien" (odblokowują fiszki). */
        final List<Long> confidentMistakes = new ArrayList<>();
        int confidentMistakeLookups = 0;
        /** Pytania, które użytkownik sam dodał do fiszek przyciskiem „Dodaj do fiszek". */
        final List<Long> manualAdditions = new ArrayList<>();
        long introducedToday = 0;
        long dueReviewsDoneToday = 0;
        /** Argumenty ostatniego zapytania o liczbę zaległych (odznaka). */
        Collection<Long> lastCountDueActIds;
        Collection<Long> lastCountDueSourceIds;
        ReviewMode lastCountDueMode;
        LocalDateTime lastCountDueAt;
        /** Ustawienia zapisane przez użytkownika (null = same domyślne z konfiguracji). */
        UserSettings stored;
        /** Pula użytkownika (test może ją zmienić: filtr źródeł, zawężenie do aktów). */
        Pool pool = Pools.all();
        ReviewQueueService service;
        final Authentication jan = FakeRepos.authentication("jan@gmail.com");

        Fixture(int dailyLimit) {
            this(dailyLimit, 20);
        }

        Fixture(int dailyLimit, int newPerDay) {
            List<User> users = new ArrayList<>();
            User user = new User();
            user.setId(1L);
            user.setPrivateEmail("jan@gmail.com");
            users.add(user);

            QuestionRepository questionRepository = FakeRepos.proxy(QuestionRepository.class, (method, args) -> {
                if (method.equals("findRefsInPool")) {
                    return QuestionRefs.inPool(questions, (Collection<Long>) args[0], (Collection<Long>) args[1]);
                }
                if (method.equals("findAllWithRelationsByIdIn")) {
                    Collection<Long> ids = (Collection<Long>) args[0];
                    loadedQuestionIds.addAll(ids);
                    return QuestionRefs.byIds(questions, ids);
                }
                throw new UnsupportedOperationException("QuestionRepository." + method);
            });
            UserQuestionStateRepository states = FakeRepos.proxy(UserQuestionStateRepository.class, (method, args) -> {
                if (method.equals("findScheduleByUserIdAndMode")) {
                    return new ArrayList<>(args[1] == ReviewMode.TEST ? testSchedule : flashcardSchedule);
                }
                if (method.equals("countDue")) {
                    lastCountDueAt = (LocalDateTime) args[2];
                    lastCountDueMode = (ReviewMode) args[1];
                    lastCountDueActIds = (Collection<Long>) args[3];
                    lastCountDueSourceIds = (Collection<Long>) args[4];
                    return 7L;
                }
                throw new UnsupportedOperationException("UserQuestionStateRepository." + method);
            });
            ReviewLogRepository logs = FakeRepos.proxy(ReviewLogRepository.class, (method, args) -> {
                switch (method) {
                    case "countFirstReviewsBetween":
                        return introducedToday;
                    case "countDueReviewsBetween":
                        return dueReviewsDoneToday;
                    case "findConfidentMistakeQuestionIds":
                        confidentMistakeLookups++;
                        return new ArrayList<>(confidentMistakes);
                    default:
                        throw new UnsupportedOperationException("ReviewLogRepository." + method);
                }
            });
            FlashcardAdditionRepository additions = FakeRepos.proxy(FlashcardAdditionRepository.class, (method, args) -> {
                if (method.equals("findQuestionIdsByUserId")) {
                    return new ArrayList<>(manualAdditions);
                }
                throw new UnsupportedOperationException("FlashcardAdditionRepository." + method);
            });

            MutableClock clock = new MutableClock(Instant.parse("2026-10-12T08:00:00Z"), ZoneId.of("Europe/Warsaw"));
            UserSettingsRepository settingsRepository = FakeRepos.proxy(UserSettingsRepository.class, (method, args) -> {
                if (method.equals("findById")) {
                    return Optional.ofNullable(stored);
                }
                throw new UnsupportedOperationException("UserSettingsRepository." + method);
            });
            CurrentUserResolver resolver = new CurrentUserResolver(FakeRepos.users(users));
            var poolService = Pools.holding(() -> pool);
            // Domyślne limity z konfiguracji: nowe fiszki, nowe pytania testowe, zaległe powtórki, retencja.
            LearningSettingsService settings = new LearningSettingsService(
                    settingsRepository, new LearningDefaults(newPerDay, 40, dailyLimit, 0.9),
                    resolver, questionRepository, states,
                    FakeRepos.proxy(ExamTermRepository.class, (method, args) -> {
                        if (method.equals("findById")) {
                            return Optional.empty();
                        }
                        throw new UnsupportedOperationException("ExamTermRepository." + method);
                    }),
                    logs, FakeRepos.proxy(TrackRepository.class, (method, args) -> {
                        throw new UnsupportedOperationException("TrackRepository." + method);
                    }),
                    poolService, null, clock);

            service = new ReviewQueueService(
                    questionRepository, states, logs, resolver,
                    new FsrsEngine(FsrsWeights.defaults(), 0.9), clock,
                    new Random(7), settings, new LeechPolicy(8), new FlashcardUnlockService(logs, additions),
                    poolService);
        }

        Question question(long id) {
            Question question = QuestionRefs.question(id);
            questions.add(question);
            return question;
        }

        Question question(long id, long actId, long fieldId, long sourceId) {
            Question question = QuestionRefs.question(id, actId, fieldId, sourceId);
            questions.add(question);
            return question;
        }

        /** Pytanie, którego fiszka jest odblokowana (pewny błąd w teście). */
        Question unlockedQuestion(long id) {
            Question question = question(id);
            confidentMistakes.add(id);
            return question;
        }

        void scheduled(ReviewMode mode, long questionId, double stability, int lastDaysAgo, int dueDaysAgo) {
            scheduled(mode, questionId, stability, lastDaysAgo, dueDaysAgo, 0, false);
        }

        /** Pytanie z historią: ostatnia powtórka {@code lastDaysAgo} dni temu, termin {@code dueDaysAgo} dni temu. */
        void scheduled(
                ReviewMode mode, long questionId, double stability, int lastDaysAgo, int dueDaysAgo,
                int lapses, boolean suspended) {
            LocalDateTime last = NOW.minusDays(lastDaysAgo);
            LocalDateTime due = NOW.minusDays(dueDaysAgo);
            (mode == ReviewMode.TEST ? testSchedule : flashcardSchedule).add(new UserQuestionStateRepository.QuestionDue() {
                public Long getQuestionId() { return questionId; }
                public LocalDateTime getDueDate() { return due; }
                public Double getStability() { return stability; }
                public LocalDateTime getLastReviewAt() { return last; }
                public Integer getLapses() { return lapses; }
                public Boolean getSuspended() { return suspended; }
            });
        }

        /**
         * Trzy zaległe pytania w trybie. Szansa przypomnienia zależy od stosunku czasu do stabilności:
         * pytanie 3 (5 dni przy stabilności 1) jest najbardziej zapomniane, potem 1 (20 dni przy 10), potem 2 (3 dni przy 2).
         * Najstarszy termin ma pytanie 1, więc kolejność "po terminie" byłaby inna: 1, 3, 2.
         */
        void threeDueQuestions(ReviewMode mode) {
            for (long id = 1; id <= 3; id++) {
                if (mode == ReviewMode.FLASHCARD) {
                    unlockedQuestion(id);
                } else {
                    question(id);
                }
            }
            scheduled(mode, 1, 10, 20, 10);
            scheduled(mode, 2, 2, 3, 1);
            scheduled(mode, 3, 1, 5, 2);
        }

        List<Long> flashQueue() {
            return service.getFlashcardQueue(null, null, null, null, null, jan).stream()
                    .map(FlashcardQueueItemResponse::questionId).toList();
        }

        List<Long> testQueue() {
            return service.getTestQueue(null, null, null, null, null, jan).stream()
                    .map(TestQueueItemResponse::questionId).toList();
        }

        BacklogResponse backlog(ReviewMode mode) {
            return service.getBacklog(null, null, null, mode, jan);
        }
    }

    @Test
    void dueFlashcardsComeInOrderOfLowestRetrievabilityNotOldestDueDate() {
        Fixture f = new Fixture(100);
        f.threeDueQuestions(ReviewMode.FLASHCARD);

        assertThat(f.flashQueue()).containsExactly(3L, 1L, 2L);
    }

    @Test
    void queueIsCappedAtTheDailyLimitAndKeepsTheMostUrgent() {
        Fixture f = new Fixture(2);
        f.threeDueQuestions(ReviewMode.FLASHCARD);

        assertThat(f.flashQueue()).containsExactly(3L, 1L);

        BacklogResponse backlog = f.backlog(ReviewMode.FLASHCARD);
        assertThat(backlog.dueNow()).isEqualTo(3L);
        assertThat(backlog.deferred()).isEqualTo(1L);
        assertThat(backlog.dailyLimit()).isEqualTo(2);
        assertThat(backlog.doneToday()).isEqualTo(0L);
    }

    @Test
    void reviewsAlreadyDoneTodayReduceTheAllowance() {
        Fixture f = new Fixture(2);
        f.threeDueQuestions(ReviewMode.FLASHCARD);
        f.dueReviewsDoneToday = 1;

        assertThat(f.flashQueue()).containsExactly(3L);
        BacklogResponse backlog = f.backlog(ReviewMode.FLASHCARD);
        assertThat(backlog.deferred()).isEqualTo(2L);
        assertThat(backlog.doneToday()).isEqualTo(1L);
    }

    @Test
    void whenTheDailyLimitIsUsedUpNoDueFlashcardsAreOfferedButNewOnesStillAre() {
        Fixture f = new Fixture(2);
        f.threeDueQuestions(ReviewMode.FLASHCARD);
        f.unlockedQuestion(9);
        f.dueReviewsDoneToday = 2;

        assertThat(f.flashQueue()).containsExactly(9L);
        assertThat(f.backlog(ReviewMode.FLASHCARD).deferred()).isEqualTo(3L);
    }

    @Test
    void extraDueReviewsAddABatchBeyondTheLimit() {
        Fixture f = new Fixture(2);
        f.threeDueQuestions(ReviewMode.FLASHCARD);
        f.dueReviewsDoneToday = 2;

        List<Long> extra = f.service.getFlashcardQueue(null, null, null, null, 2, f.jan)
                .stream().map(FlashcardQueueItemResponse::questionId).toList();

        assertThat(extra).containsExactly(3L, 1L);
    }

    @Test
    void negativeExtraDueReviewsMeansNone() {
        Fixture f = new Fixture(100);
        f.threeDueQuestions(ReviewMode.FLASHCARD);

        assertThat(f.service.getFlashcardQueue(null, null, null, null, -5, f.jan)).isEmpty();
    }

    @Test
    void questionsNotYetDueAreNotInTheQueue() {
        Fixture f = new Fixture(100);
        f.threeDueQuestions(ReviewMode.FLASHCARD);
        f.unlockedQuestion(4);
        f.scheduled(ReviewMode.FLASHCARD, 4, 5, 1, -3); // termin za 3 dni

        assertThat(f.flashQueue()).containsExactly(3L, 1L, 2L);
        assertThat(f.backlog(ReviewMode.FLASHCARD).dueNow()).isEqualTo(3L);
    }

    @Test
    void newFlashcardsFollowTheDueOnesInIdOrderUpToTheDailyLimit() {
        Fixture f = new Fixture(100, 2);
        f.threeDueQuestions(ReviewMode.FLASHCARD);
        f.unlockedQuestion(30);
        f.unlockedQuestion(10);
        f.unlockedQuestion(20);

        assertThat(f.flashQueue()).containsExactly(3L, 1L, 2L, 10L, 20L);

        f.introducedToday = 1; // jedna nowa już dziś, zostaje jedno miejsce
        assertThat(f.flashQueue()).containsExactly(3L, 1L, 2L, 10L);
    }

    @Test
    void testQueueHoldsTheMostUrgentDueQuestionsOfTheTestMode() {
        Fixture f = new Fixture(2);
        f.threeDueQuestions(ReviewMode.TEST);
        f.unlockedQuestion(50);
        f.scheduled(ReviewMode.FLASHCARD, 50, 1, 30, 20); // zaległa fiszka, ale to inny tryb

        List<Long> queue = f.testQueue();

        // Mieszanie zmienia kolejność, ale zestaw zaległych to dwa najpilniejsze pytania.
        assertThat(queue.subList(0, 2)).containsExactlyInAnyOrder(3L, 1L);
        assertThat(f.backlog(ReviewMode.TEST).dueNow()).isEqualTo(3L);
        // Tryby mają osobne stany: w zaległościach fiszek jest tylko fiszka 50.
        assertThat(f.backlog(ReviewMode.FLASHCARD).dueNow()).isEqualTo(1L);
    }

    @Test
    void zeroNewQuestionsGivesADueOnlyTestSession() {
        Fixture f = new Fixture(100);
        f.threeDueQuestions(ReviewMode.TEST);
        f.question(50); // pytania bez historii: zwykły test by je dołożył
        f.question(51);

        List<Long> dueOnly = f.service.getTestQueue(null, null, null, 0, null, f.jan)
                .stream().map(TestQueueItemResponse::questionId).toList();

        // To jest sesja „Powtórz zaległe": same pytania z minionym terminem, ani jednego nowego.
        assertThat(dueOnly).containsExactlyInAnyOrder(1L, 2L, 3L);
        // Kontrola, że test coś mierzy: bez parametru nowe pytania dochodzą do zaległych.
        assertThat(f.testQueue()).containsExactlyInAnyOrder(1L, 2L, 3L, 50L, 51L);
        // Pełne treści ładują się tylko dla pytań z sesji (3 + 5).
        assertThat(f.loadedQuestionIds).hasSize(8);
    }

    @Test
    void aDueOnlyTestSessionStillRespectsTheDailyReviewLimit() {
        Fixture f = new Fixture(2);
        f.threeDueQuestions(ReviewMode.TEST);
        f.question(50);

        List<Long> dueOnly = f.service.getTestQueue(null, null, null, 0, null, f.jan)
                .stream().map(TestQueueItemResponse::questionId).toList();

        // Limit 2 zaległych dziennie: dwa najpilniejsze pytania, bez nowego 50.
        assertThat(dueOnly).containsExactlyInAnyOrder(3L, 1L);
    }

    @Test
    void aDueOnlyTestSessionIsEmptyWhenNothingIsDueAndLoadsNothing() {
        Fixture f = new Fixture(100);
        f.question(50);
        f.question(51);
        f.question(52);
        f.scheduled(ReviewMode.TEST, 52, 5, 1, -3); // termin za 3 dni

        assertThat(f.service.getTestQueue(null, null, null, 0, null, f.jan)).isEmpty();
        assertThat(f.loadedQuestionIds).isEmpty();
    }

    @Test
    void availableNewQuestionsCountsOnlyQuestionsWithoutHistoryInTheMode() {
        Fixture f = new Fixture(100);
        f.threeDueQuestions(ReviewMode.FLASHCARD);
        f.unlockedQuestion(8);
        f.unlockedQuestion(9);

        assertThat(f.service.countAvailableNewQuestions(null, null, null, ReviewMode.FLASHCARD, f.jan)).isEqualTo(2);
        // W teście pytania 1-3 są ruszone tylko w fiszkach, więc w teście wszystkie pięć jest nowych.
        assertThat(f.service.countAvailableNewQuestions(null, null, null, ReviewMode.TEST, f.jan)).isEqualTo(5);
    }

    // ------------------------------------------------------------------ limity z ustawień użytkownika

    private static UserSettings stored(Integer newFlashcards, Integer newTests, Integer maxReviews) {
        UserSettings settings = new UserSettings(1L);
        settings.setNewFlashcardsPerDay(newFlashcards);
        settings.setNewTestQuestionsPerDay(newTests);
        settings.setMaxReviewsPerDay(maxReviews);
        return settings;
    }

    @Test
    void theUsersOwnReviewLimitReplacesTheDefault() {
        Fixture f = new Fixture(100);              // domyślnie 100 powtórek dziennie
        f.threeDueQuestions(ReviewMode.FLASHCARD);
        f.stored = stored(null, null, 1);          // użytkownik ustawił 1

        assertThat(f.flashQueue()).containsExactly(3L);
        BacklogResponse backlog = f.backlog(ReviewMode.FLASHCARD);
        assertThat(backlog.dailyLimit()).isEqualTo(1);
        assertThat(backlog.deferred()).isEqualTo(2L);
    }

    @Test
    void anUnsetReviewLimitFallsBackToTheServerDefault() {
        Fixture f = new Fixture(2);
        f.threeDueQuestions(ReviewMode.FLASHCARD);
        f.stored = stored(5, 5, null);             // inne limity ustawione, ten nie

        assertThat(f.flashQueue()).containsExactly(3L, 1L);
        assertThat(f.backlog(ReviewMode.FLASHCARD).dailyLimit()).isEqualTo(2);
    }

    @Test
    void theUsersOwnLimitOfNewFlashcardsReplacesTheDefault() {
        Fixture f = new Fixture(100, 20);          // domyślnie 20 nowych dziennie
        f.unlockedQuestion(10);
        f.unlockedQuestion(20);
        f.unlockedQuestion(30);
        f.stored = stored(2, null, null);

        assertThat(f.flashQueue()).containsExactly(10L, 20L);
    }

    @Test
    void testsUseTheirOwnNewQuestionLimit_notTheFlashcardOne() {
        Fixture f = new Fixture(100, 20);
        for (long id = 10; id <= 13; id++) {
            f.unlockedQuestion(id);
        }
        f.stored = stored(1, 2, null);             // fiszki: 1 nowa, testy: 2 nowe

        assertThat(f.testQueue()).hasSize(2);
        assertThat(f.flashQueue()).hasSize(1);
    }

    @Test
    void aZeroNewFlashcardLimitOffersNoNewFlashcardsButKeepsTheDueOnes() {
        Fixture f = new Fixture(100);
        f.threeDueQuestions(ReviewMode.FLASHCARD);
        f.unlockedQuestion(9);
        f.stored = stored(0, null, null);

        assertThat(f.flashQueue()).containsExactly(3L, 1L, 2L);
    }

    // ---------------------------------------------------------------- zawieszone i trudne pytania

    @Test
    void suspendedFlashcardIsNotInTheQueue_andIsNotMistakenForANewOne() {
        Fixture f = new Fixture(100);
        f.threeDueQuestions(ReviewMode.FLASHCARD);
        f.unlockedQuestion(4);
        f.scheduled(ReviewMode.FLASHCARD, 4, 10, 40, 30, 0, true);    // zawieszona (ma stan), zaległa od dawna
        f.unlockedQuestion(5);                                         // nowa, bez stanu

        assertThat(f.flashQueue()).containsExactly(3L, 1L, 2L, 5L);
        assertThat(f.service.countAvailableNewQuestions(null, null, null, ReviewMode.FLASHCARD, f.jan)).isEqualTo(1);
    }

    @Test
    void suspendedQuestionIsNotInTheTestQueue_andNotInTheBacklog() {
        Fixture f = new Fixture(100);
        f.threeDueQuestions(ReviewMode.TEST);
        f.question(4);
        f.scheduled(ReviewMode.TEST, 4, 10, 40, 30, 0, true);

        assertThat(f.testQueue()).containsExactlyInAnyOrder(3L, 1L, 2L);
        assertThat(f.backlog(ReviewMode.TEST).dueNow()).isEqualTo(3L);
        // Zawieszenie w teście nie dotyczy fiszek: tam nic nie czeka.
        assertThat(f.backlog(ReviewMode.FLASHCARD).dueNow()).isEqualTo(0L);
    }

    @Test
    void suspendedFlashcardDoesNotUseUpTheDailyReviewLimit() {
        Fixture f = new Fixture(2);
        f.threeDueQuestions(ReviewMode.FLASHCARD);
        f.unlockedQuestion(4);
        f.scheduled(ReviewMode.FLASHCARD, 4, 1, 60, 50, 0, true);     // najbardziej zapomniana, ale zawieszona

        assertThat(f.flashQueue()).containsExactly(3L, 1L);
        assertThat(f.backlog(ReviewMode.FLASHCARD).deferred()).isEqualTo(1L);
    }

    @Test
    void flashcardQueueCarriesLapsesAndTheDifficultFlag() {
        Fixture f = new Fixture(100);
        f.unlockedQuestion(1);
        f.unlockedQuestion(2);
        f.unlockedQuestion(3);
        f.scheduled(ReviewMode.FLASHCARD, 1, 10, 12, 2, 8, false);    // próg 8: trudne
        f.scheduled(ReviewMode.FLASHCARD, 2, 10, 20, 10, 7, false);   // jeszcze nie

        List<FlashcardQueueItemResponse> queue = f.service.getFlashcardQueue(null, null, null, null, null, f.jan);

        FlashcardQueueItemResponse hard = queue.stream().filter(i -> i.questionId() == 1L).findFirst().orElseThrow();
        FlashcardQueueItemResponse almost = queue.stream().filter(i -> i.questionId() == 2L).findFirst().orElseThrow();
        FlashcardQueueItemResponse brandNew = queue.stream().filter(i -> i.questionId() == 3L).findFirst().orElseThrow();
        assertThat(hard.lapses()).isEqualTo(8);
        assertThat(hard.leech()).isTrue();
        assertThat(almost.lapses()).isEqualTo(7);
        assertThat(almost.leech()).isFalse();
        assertThat(brandNew.lapses()).isZero();
        assertThat(brandNew.leech()).isFalse();
    }

    @Test
    void flashcardShowsTheQuestionOnTheFrontAndTheCorrectAnswerOnTheBack() {
        Fixture f = new Fixture(100);
        f.unlockedQuestion(1);

        FlashcardQueueItemResponse card = f.service.getFlashcardQueue(null, null, null, null, null, f.jan).get(0);

        assertThat(card.front()).isEqualTo("P1");
        assertThat(card.back()).isEqualTo("a");
    }

    // ---------------------------------------------------------------- ładowanie tylko tego, co potrzebne

    @Test
    void onlyTheQuestionsOfTheSessionAreLoadedInFull_notTheWholePool() {
        Fixture f = new Fixture(100, 20);
        for (long id = 1; id <= 300; id++) {
            f.unlockedQuestion(id);
        }

        List<Long> queue = f.flashQueue();

        assertThat(queue).hasSize(20);
        // z 300 dostępnych pytań pełne treści ładuje się tylko dla 20 z sesji, w kolejności sesji
        assertThat(f.loadedQuestionIds).hasSize(20).containsExactlyElementsOf(queue);
    }

    @Test
    void testQueueLoadsFullQuestionsOnlyForTheSession() {
        Fixture f = new Fixture(100);
        f.threeDueQuestions(ReviewMode.TEST);
        for (long id = 10; id < 200; id++) {
            f.question(id);
        }

        List<Long> queue = f.testQueue();

        // 3 zaległe + dzienny limit nowych pytań testowych (40), z 190 dostępnych nowych
        assertThat(queue).hasSize(3 + 40);
        assertThat(f.loadedQuestionIds).hasSize(43).containsExactlyInAnyOrderElementsOf(queue);
    }

    @Test
    void backlogAndNewQuestionCountsLoadNoFullQuestionsAtAll() {
        Fixture f = new Fixture(2);
        f.threeDueQuestions(ReviewMode.TEST);
        f.question(50);

        f.backlog(ReviewMode.TEST);
        f.backlog(ReviewMode.FLASHCARD);
        f.service.countAvailableNewQuestions(null, null, null, ReviewMode.FLASHCARD, f.jan);
        f.service.countAvailableNewQuestions(null, null, null, ReviewMode.TEST, f.jan);

        assertThat(f.loadedQuestionIds).isEmpty();
    }

    @Test
    void anEmptyQueueDoesNotAskTheDatabaseForQuestions() {
        Fixture f = new Fixture(100);

        assertThat(f.flashQueue()).isEmpty();
        assertThat(f.testQueue()).isEmpty();
        assertThat(f.loadedQuestionIds).isEmpty();
    }

    // ---------------------------------------------------------------- Fiszki i Testy to dwa tryby tych samych pytań

    @Test
    void theFlashcardQueueHoldsOnlyUnlockedQuestions_theTestQueueHoldsTheWholePool() {
        Fixture f = new Fixture(100);
        f.question(1);
        f.unlockedQuestion(2);
        f.question(3);
        f.unlockedQuestion(4);

        assertThat(f.flashQueue()).containsExactly(2L, 4L);
        assertThat(f.testQueue()).containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
    }

    @Test
    void newQuestionCountsFollowTheMode() {
        Fixture f = new Fixture(100);
        f.question(1);
        f.unlockedQuestion(2);
        f.unlockedQuestion(3);

        assertThat(f.service.countAvailableNewQuestions(null, null, null, ReviewMode.FLASHCARD, f.jan)).isEqualTo(2);
        assertThat(f.service.countAvailableNewQuestions(null, null, null, ReviewMode.TEST, f.jan)).isEqualTo(3);
    }

    @Test
    void theSameQuestionHasASeparateStateInEachMode() {
        Fixture f = new Fixture(100);
        f.unlockedQuestion(1);
        f.scheduled(ReviewMode.TEST, 1, 5, 10, 5);       // w teście zaległe
        f.scheduled(ReviewMode.FLASHCARD, 1, 5, 10, -4); // w fiszkach dopiero za cztery dni

        assertThat(f.backlog(ReviewMode.TEST).dueNow()).isEqualTo(1L);
        assertThat(f.backlog(ReviewMode.FLASHCARD).dueNow()).isEqualTo(0L);
        assertThat(f.testQueue()).containsExactly(1L);
        assertThat(f.flashQueue()).isEmpty();
    }

    // ---------------------------------------------------------------- odblokowanie fiszek

    @Test
    void aFlashcardIsHiddenUntilTheUserConfidentlyMissesItsQuestion() {
        Fixture f = new Fixture(100);
        f.question(1);

        assertThat(f.flashQueue()).isEmpty();
        assertThat(f.service.countAvailableNewQuestions(null, null, null, ReviewMode.FLASHCARD, f.jan)).isEqualTo(0);
        assertThat(f.backlog(ReviewMode.FLASHCARD).dueNow()).isEqualTo(0L);
    }

    @Test
    void aFlashcardAppearsAsANewOneAfterAConfidentMistake() {
        Fixture f = new Fixture(100);
        f.question(1);
        f.question(2);
        f.confidentMistakes.add(1L);

        assertThat(f.flashQueue()).containsExactly(1L);
        assertThat(f.service.countAvailableNewQuestions(null, null, null, ReviewMode.FLASHCARD, f.jan)).isEqualTo(1);
    }

    @Test
    void aFlashcardAppearsAsANewOneAfterTheUserAddsItManually() {
        Fixture f = new Fixture(100);
        f.question(1);
        f.question(2);
        f.manualAdditions.add(2L);

        assertThat(f.flashQueue()).containsExactly(2L);
        assertThat(f.service.countAvailableNewQuestions(null, null, null, ReviewMode.FLASHCARD, f.jan)).isEqualTo(1);
    }

    @Test
    void aManualAdditionAndAConfidentMistakeOnTheSameQuestionShowTheFlashcardOnlyOnce() {
        Fixture f = new Fixture(100);
        f.question(1);
        f.confidentMistakes.add(1L);
        f.manualAdditions.add(1L);

        assertThat(f.flashQueue()).containsExactly(1L);
    }

    @Test
    void aMistakeInADifferentQuestionDoesNotUnlockTheFlashcard() {
        Fixture f = new Fixture(100);
        f.question(1);
        f.question(5);
        f.confidentMistakes.add(5L);

        assertThat(f.flashQueue()).containsExactly(5L);
    }

    @Test
    void aHiddenFlashcardNeverAppearsEvenWhenItsQuestionWasMissed() {
        Fixture f = new Fixture(100);
        Question question = f.question(1);
        question.setFlashcardHidden(true);
        f.confidentMistakes.add(1L);
        f.manualAdditions.add(1L);

        assertThat(f.flashQueue()).isEmpty();
        // ale pytanie zostaje w testach
        assertThat(f.testQueue()).containsExactly(1L);
    }

    @Test
    void anUnlockedFlashcardKeepsItsOwnScheduleAndComesBackWhenDue() {
        Fixture f = new Fixture(100);
        f.unlockedQuestion(1);
        f.scheduled(ReviewMode.FLASHCARD, 1, 3, 6, 2);

        assertThat(f.flashQueue()).containsExactly(1L);
        assertThat(f.backlog(ReviewMode.FLASHCARD).dueNow()).isEqualTo(1L);
    }

    @Test
    void theTestQueueDoesNotLookUpUnlocks() {
        Fixture f = new Fixture(100);
        f.question(1);

        assertThat(f.testQueue()).containsExactly(1L);
        assertThat(f.confidentMistakeLookups).isZero();
    }

    // ---------------------------------------------------------------- zakres kolejki

    @Test
    void scopeNarrowsTheQueueToASourceAnActOrAField_theNarrowestWins() {
        Fixture f = new Fixture(100);
        f.question(1, 1, 1, QuestionRefs.OWN_SHEET);
        f.question(2, 1, 1, QuestionRefs.OTHER_SHEET);
        f.question(3, 2, 2, QuestionRefs.OWN_SHEET);

        assertThat(f.service.getTestQueue(2L, null, null, null, null, f.jan))
                .extracting(TestQueueItemResponse::questionId).containsExactly(3L);
        assertThat(f.service.getTestQueue(null, 1L, null, null, null, f.jan))
                .extracting(TestQueueItemResponse::questionId).containsExactlyInAnyOrder(1L, 2L);
        assertThat(f.service.getTestQueue(null, null, QuestionRefs.OTHER_SHEET, null, null, f.jan))
                .extracting(TestQueueItemResponse::questionId).containsExactly(2L);
        // źródło jest węższe niż akt i dziedzina
        assertThat(f.service.getTestQueue(2L, 2L, QuestionRefs.OWN_SHEET, null, null, f.jan))
                .extracting(TestQueueItemResponse::questionId).containsExactlyInAnyOrder(1L, 3L);
    }

    // ---------------------------------------------------------------- pula: akty i filtr źródeł

    @Test
    void questionsOutsideTheActsOfThePoolAreNotOffered() {
        Fixture f = new Fixture(100);
        f.question(1, 1, 1, QuestionRefs.OWN_SHEET);
        f.question(2, 9, 3, QuestionRefs.OWN_SHEET); // akt spoza puli
        f.pool = Pools.of(SourceFilter.ALL, 1);

        assertThat(f.testQueue()).containsExactly(1L);
    }

    @Test
    void theUsersNarrowingToActsLimitsTheQueue() {
        Fixture f = new Fixture(100);
        f.question(1, 1, 1, QuestionRefs.OWN_SHEET);
        f.question(2, 2, 1, QuestionRefs.OWN_SHEET);
        f.pool = Pools.narrowedTo(Pools.all(), 2);

        assertThat(f.testQueue()).containsExactly(2L);
    }

    @Test
    void testQueueContainsOnlyQuestionsFromTheSelectedSourceGroups() {
        Fixture f = new Fixture(100);
        f.question(1, 1, 1, QuestionRefs.OWN_SHEET);
        f.question(2, 1, 1, QuestionRefs.OTHER_SHEET);
        f.question(3, 1, 1, QuestionRefs.AUTHORED);
        f.question(4, 1, 1, QuestionRefs.OWN_SHEET);
        f.pool = Pools.of(new SourceFilter(true, false, true), 1, 2);

        assertThat(f.testQueue()).containsExactlyInAnyOrder(1L, 3L, 4L);
        assertThat(f.loadedQuestionIds).doesNotContain(2L);
    }

    @Test
    void dueQuestionsFromFilteredOutSourcesStayOutOfTheQueueAndTheBacklog() {
        Fixture f = new Fixture(100);
        f.question(1, 1, 1, QuestionRefs.OWN_SHEET);
        f.question(2, 1, 1, QuestionRefs.OTHER_SHEET);
        f.scheduled(ReviewMode.TEST, 1, 3, 5, 2);
        f.scheduled(ReviewMode.TEST, 2, 3, 5, 2);
        f.pool = Pools.of(new SourceFilter(true, false, false), 1, 2);

        assertThat(f.testQueue()).containsExactly(1L);
        assertThat(f.backlog(ReviewMode.TEST).dueNow()).isEqualTo(1L);
    }

    @Test
    void theFlashcardQueueIgnoresTheSourceFilter() {
        Fixture f = new Fixture(100);
        f.unlockedQuestion(1);                                        // własny arkusz
        f.question(2, 1, 1, QuestionRefs.OTHER_SHEET);
        f.confidentMistakes.add(2L);                                   // arkusz innej ścieżki, odblokowana fiszka
        f.pool = Pools.of(new SourceFilter(true, false, false), 1, 2);

        assertThat(f.flashQueue()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(f.testQueue()).containsExactly(1L);
    }

    @Test
    void availableNewQuestionsAreCountedWithinTheFilter_butNewFlashcardsAreNot() {
        Fixture f = new Fixture(100);
        f.question(1, 1, 1, QuestionRefs.OWN_SHEET);
        f.question(2, 1, 1, QuestionRefs.OTHER_SHEET);
        f.question(3, 1, 1, QuestionRefs.OTHER_SHEET);
        f.confidentMistakes.addAll(List.of(2L, 3L));
        f.pool = Pools.of(new SourceFilter(true, false, false), 1, 2);

        assertThat(f.service.countAvailableNewQuestions(null, null, null, ReviewMode.TEST, f.jan)).isEqualTo(1);
        assertThat(f.service.countAvailableNewQuestions(null, null, null, ReviewMode.FLASHCARD, f.jan)).isEqualTo(2);
    }

    @Test
    void theDueBadgeForTestsUsesTheFilter_theBadgeForFlashcardsUsesEverySource() {
        Fixture f = new Fixture(100);
        f.pool = Pools.of(new SourceFilter(true, true, false), 1, 2);

        assertThat(f.service.countDueNow(ReviewMode.TEST, f.jan)).isEqualTo(7L);
        assertThat(f.lastCountDueMode).isEqualTo(ReviewMode.TEST);
        assertThat(f.lastCountDueActIds).containsExactlyInAnyOrder(1L, 2L);
        assertThat(f.lastCountDueSourceIds).containsExactlyInAnyOrder(QuestionRefs.OWN_SHEET, QuestionRefs.OTHER_SHEET);

        f.service.countDueNow(ReviewMode.FLASHCARD, f.jan);
        assertThat(f.lastCountDueMode).isEqualTo(ReviewMode.FLASHCARD);
        assertThat(f.lastCountDueSourceIds).containsExactlyInAnyOrderElementsOf(Pools.ALL_SOURCES);
    }

    @Test
    void theTomorrowCountReachesToTheEndOfTomorrowAndUsesTheSamePool() {
        Fixture f = new Fixture(100);
        f.pool = Pools.of(new SourceFilter(true, false, false), 1, 2);

        assertThat(f.service.countDueByEndOfTomorrow(ReviewMode.TEST, f.jan)).isEqualTo(7L);

        // dziś 12 października (czas warszawski), więc granica to koniec 13 października
        assertThat(f.lastCountDueAt).isEqualTo(LocalDateTime.of(2026, 10, 13, 23, 59, 59, 999_999_999));
        assertThat(f.lastCountDueSourceIds).containsExactly(QuestionRefs.OWN_SHEET);

        f.service.countDueByEndOfTomorrow(ReviewMode.FLASHCARD, f.jan);
        assertThat(f.lastCountDueSourceIds).containsExactlyInAnyOrderElementsOf(Pools.ALL_SOURCES);
    }
}
