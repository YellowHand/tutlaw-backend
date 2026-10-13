package pl.tutlaw.backend.review;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import pl.tutlaw.backend.card.Card;
import pl.tutlaw.backend.card.CardRepository;
import pl.tutlaw.backend.card.CorrectOption;
import pl.tutlaw.backend.chapter.Chapter;
import pl.tutlaw.backend.review.dto.BacklogResponse;
import pl.tutlaw.backend.review.dto.ReviewQueueItemResponse;
import pl.tutlaw.backend.review.dto.TestQueueItemResponse;
import pl.tutlaw.backend.review.fsrs.FsrsEngine;
import pl.tutlaw.backend.review.fsrs.FsrsWeights;
import pl.tutlaw.backend.settings.LearningDefaults;
import pl.tutlaw.backend.settings.LearningSettingsService;
import pl.tutlaw.backend.settings.UserSettings;
import pl.tutlaw.backend.settings.UserSettingsRepository;
import pl.tutlaw.backend.testsupport.CardRefs;
import pl.tutlaw.backend.testsupport.FakeRepos;
import pl.tutlaw.backend.testsupport.MutableClock;
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

/** Układanie kolejki powtórek na prawdziwym ReviewQueueService, z repozytoriami "w pamięci". */
class ReviewQueueServiceTest {

    /** 2026-10-12 08:00 UTC to 10:00 w Warszawie. */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 10, 0);

    private static final class Fixture {
        final List<Card> cards = new ArrayList<>();
        final List<UserCardStateRepository.CardDue> schedule = new ArrayList<>();
        /** Numery kart, o których pełne treści poproszono bazę (powinny to być tylko karty z sesji). */
        final List<Long> loadedCardIds = new ArrayList<>();
        /** Pytania testowe, które użytkownik pomylił bez „Nie jestem pewien" (odblokowują powiązane fiszki). */
        final List<Long> confidentMistakes = new ArrayList<>();
        int confidentMistakeLookups = 0;
        long introducedToday = 0;
        long dueReviewsDoneToday = 0;
        /** Ustawienia zapisane przez użytkownika (null = same domyślne z konfiguracji). */
        UserSettings stored;
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

            CardRepository cardRepository = FakeRepos.proxy(CardRepository.class, (method, args) -> {
                if (method.startsWith("findReadableRefsForUser")) {
                    return CardRefs.of(cards);
                }
                if (method.equals("findAllWithSourceByIdIn")) {
                    Collection<Long> ids = (Collection<Long>) args[0];
                    loadedCardIds.addAll(ids);
                    return CardRefs.byIds(cards, ids);
                }
                throw new UnsupportedOperationException("CardRepository." + method);
            });
            UserCardStateRepository states = FakeRepos.proxy(UserCardStateRepository.class, (method, args) -> {
                if (method.equals("findCardDueByUserId")) {
                    return new ArrayList<>(schedule);
                }
                throw new UnsupportedOperationException("UserCardStateRepository." + method);
            });
            ReviewLogRepository logs = FakeRepos.proxy(ReviewLogRepository.class, (method, args) -> {
                switch (method) {
                    case "countFirstReviewsBetween":
                        return introducedToday;
                    case "countDueReviewsBetween":
                        return dueReviewsDoneToday;
                    case "findConfidentMistakeCardIds":
                        confidentMistakeLookups++;
                        return new ArrayList<>(confidentMistakes);
                    default:
                        throw new UnsupportedOperationException("ReviewLogRepository." + method);
                }
            });

            MutableClock clock = new MutableClock(Instant.parse("2026-10-12T08:00:00Z"), ZoneId.of("Europe/Warsaw"));
            UserSettingsRepository settingsRepository = FakeRepos.proxy(UserSettingsRepository.class, (method, args) -> {
                if (method.equals("findById")) {
                    return Optional.ofNullable(stored);
                }
                throw new UnsupportedOperationException("UserSettingsRepository." + method);
            });
            CurrentUserResolver resolver = new CurrentUserResolver(FakeRepos.users(users));
            // Domyślne limity z konfiguracji: nowe fiszki, nowe pytania testowe, zaległe powtórki, retencja.
            LearningSettingsService settings = new LearningSettingsService(
                    settingsRepository, new LearningDefaults(newPerDay, 40, dailyLimit, 0.9),
                    resolver, cardRepository, states, clock);

            service = new ReviewQueueService(
                    cardRepository, states, logs, resolver,
                    new FsrsEngine(FsrsWeights.defaults(), 0.9), clock,
                    new Random(7), settings, new LeechPolicy(8));
        }

        Card card(long id, boolean test, int chapterOrder) {
            Card card = new Card();
            card.setId(id);
            card.setFront("P" + id);
            card.setBack("O" + id);
            if (test) {
                card.setOptionA("a");
                card.setOptionB("b");
                card.setOptionC("c");
                card.setCorrectOption(CorrectOption.A);
            }
            Chapter chapter = new Chapter();
            chapter.setOrderIndex(chapterOrder);
            card.setChapter(chapter);
            cards.add(card);
            return card;
        }

        /** Fiszka (bez opcji) powiązana z pytaniem testowym {@code testCardId}: ukryta do pierwszego pewnego błędu. */
        Card linkedFlashcard(long id, long testCardId, int chapterOrder) {
            Card card = card(id, false, chapterOrder);
            card.setLinkedTestCardId(testCardId);
            return card;
        }

        /** Karta z historią: ostatnia powtórka {@code lastDaysAgo} dni temu, termin {@code dueDaysAgo} dni temu. */
        void scheduled(long cardId, double stability, int lastDaysAgo, int dueDaysAgo) {
            scheduled(cardId, stability, lastDaysAgo, dueDaysAgo, 0, false);
        }

        void scheduled(long cardId, double stability, int lastDaysAgo, int dueDaysAgo, int lapses, boolean suspended) {
            LocalDateTime last = NOW.minusDays(lastDaysAgo);
            LocalDateTime due = NOW.minusDays(dueDaysAgo);
            schedule.add(new UserCardStateRepository.CardDue() {
                public Long getCardId() { return cardId; }
                public LocalDateTime getDueDate() { return due; }
                public Double getStability() { return stability; }
                public LocalDateTime getLastReviewAt() { return last; }
                public Integer getLapses() { return lapses; }
                public Boolean getSuspended() { return suspended; }
            });
        }

        /**
         * Trzy zaległe karty. Szansa przypomnienia zależy od stosunku czasu do stabilności:
         * karta 3 (5 dni przy stabilności 1) jest najbardziej zapomniana, potem 1 (20 dni przy 10), potem 2 (3 dni przy 2).
         * Najstarszy termin ma karta 1, więc kolejność "po terminie" byłaby inna: 1, 3, 2.
         */
        void threeDueCards(boolean test) {
            card(1, test, 1);
            card(2, test, 1);
            card(3, test, 1);
            scheduled(1, 10, 20, 10);
            scheduled(2, 2, 3, 1);
            scheduled(3, 1, 5, 2);
        }

        List<Long> flashQueue() {
            return service.getQueue(null, null, null, null, null, jan).stream().map(ReviewQueueItemResponse::cardId).toList();
        }

        List<Long> testQueue() {
            return service.getTestQueue(null, null, null, null, null, jan).stream().map(TestQueueItemResponse::cardId).toList();
        }
    }

    @Test
    void dueCardsComeInOrderOfLowestRetrievabilityNotOldestDueDate() {
        Fixture f = new Fixture(100);
        f.threeDueCards(false);

        assertThat(f.flashQueue()).containsExactly(3L, 1L, 2L);
    }

    @Test
    void queueIsCappedAtTheDailyLimitAndKeepsTheMostUrgent() {
        Fixture f = new Fixture(2);
        f.threeDueCards(false);

        assertThat(f.flashQueue()).containsExactly(3L, 1L);

        BacklogResponse backlog = f.service.getBacklog(null, null, null, ReviewMode.FLASHCARD, f.jan);
        assertThat(backlog.dueNow()).isEqualTo(3L);
        assertThat(backlog.deferred()).isEqualTo(1L);
        assertThat(backlog.dailyLimit()).isEqualTo(2);
        assertThat(backlog.doneToday()).isEqualTo(0L);
    }

    @Test
    void reviewsAlreadyDoneTodayReduceTheAllowance() {
        Fixture f = new Fixture(2);
        f.threeDueCards(false);
        f.dueReviewsDoneToday = 1;

        assertThat(f.flashQueue()).containsExactly(3L);
        BacklogResponse backlog = f.service.getBacklog(null, null, null, ReviewMode.FLASHCARD, f.jan);
        assertThat(backlog.deferred()).isEqualTo(2L);
        assertThat(backlog.doneToday()).isEqualTo(1L);
    }

    @Test
    void whenTheDailyLimitIsUsedUpNoDueCardsAreOfferedButNewOnesStillAre() {
        Fixture f = new Fixture(2);
        f.threeDueCards(false);
        f.card(9, false, 1);
        f.dueReviewsDoneToday = 2;

        assertThat(f.flashQueue()).containsExactly(9L);
        assertThat(f.service.getBacklog(null, null, null, ReviewMode.FLASHCARD, f.jan).deferred()).isEqualTo(3L);
    }

    @Test
    void extraDueReviewsAddABatchBeyondTheLimit() {
        Fixture f = new Fixture(2);
        f.threeDueCards(false);
        f.dueReviewsDoneToday = 2;

        List<Long> extra = f.service.getQueue(null, null, null, null, 2, f.jan)
                .stream().map(ReviewQueueItemResponse::cardId).toList();

        assertThat(extra).containsExactly(3L, 1L);
    }

    @Test
    void negativeExtraDueReviewsMeansNone() {
        Fixture f = new Fixture(100);
        f.threeDueCards(false);

        assertThat(f.service.getQueue(null, null, null, null, -5, f.jan)).isEmpty();
    }

    @Test
    void cardsNotYetDueAreNotInTheQueue() {
        Fixture f = new Fixture(100);
        f.threeDueCards(false);
        f.card(4, false, 1);
        f.scheduled(4, 5, 1, -3); // termin za 3 dni

        assertThat(f.flashQueue()).containsExactly(3L, 1L, 2L);
        assertThat(f.service.getBacklog(null, null, null, ReviewMode.FLASHCARD, f.jan).dueNow()).isEqualTo(3L);
    }

    @Test
    void newCardsFollowTheDueOnesInChapterOrderUpToTheDailyLimit() {
        Fixture f = new Fixture(100, 2);
        f.threeDueCards(false);
        f.card(30, false, 3);
        f.card(10, false, 1);
        f.card(20, false, 2);

        assertThat(f.flashQueue()).containsExactly(3L, 1L, 2L, 10L, 20L);

        f.introducedToday = 1; // jedna nowa już dziś, zostaje jedno miejsce
        assertThat(f.flashQueue()).containsExactly(3L, 1L, 2L, 10L);
    }

    @Test
    void testQueueHoldsOnlyTestQuestionsAndTheSameUrgentSet() {
        Fixture f = new Fixture(2);
        f.threeDueCards(true);
        f.card(50, false, 1);           // zwykła fiszka, bez opcji ABC
        f.scheduled(50, 1, 30, 20);     // zaległa i bardzo zapomniana, ale to nie pytanie testowe

        List<Long> queue = f.testQueue();

        // Mieszanie zmienia kolejność, ale zestaw to dwa najpilniejsze pytania testowe.
        assertThat(queue).hasSize(2);
        assertThat(queue.containsAll(List.of(3L, 1L))).isTrue();
        assertThat(f.service.getBacklog(null, null, null, ReviewMode.TEST, f.jan).dueNow()).isEqualTo(3L);
        // Fiszki i Testy są osobno: w zaległościach fiszek jest tylko zwykła fiszka 50, bez pytań testowych.
        assertThat(f.service.getBacklog(null, null, null, ReviewMode.FLASHCARD, f.jan).dueNow()).isEqualTo(1L);
    }

    @Test
    void zeroNewQuestionsGivesADueOnlyTestSession() {
        Fixture f = new Fixture(100);
        f.threeDueCards(true);
        f.card(50, true, 1); // pytania bez historii: zwykły test by je dołożył
        f.card(51, true, 1);

        List<Long> dueOnly = f.service.getTestQueue(null, null, null, 0, null, f.jan)
                .stream().map(TestQueueItemResponse::cardId).toList();

        // To jest sesja „Powtórz zaległe": same pytania z minionym terminem, ani jednego nowego.
        assertThat(dueOnly).containsExactlyInAnyOrder(1L, 2L, 3L);
        // Kontrola, że test coś mierzy: bez parametru nowe pytania dochodzą do zaległych.
        assertThat(f.testQueue()).containsExactlyInAnyOrder(1L, 2L, 3L, 50L, 51L);
        // Pełne treści ładują się tylko dla pytań z sesji.
        assertThat(f.loadedCardIds).hasSize(8);
    }

    @Test
    void aDueOnlyTestSessionStillRespectsTheDailyReviewLimitAndSkipsTestsFromOtherWorlds() {
        Fixture f = new Fixture(2);
        f.threeDueCards(true);
        f.card(50, true, 1);
        f.card(60, false, 1);        // fiszka: zaległa, ale to nie Testy
        f.scheduled(60, 1, 30, 20);

        List<Long> dueOnly = f.service.getTestQueue(null, null, null, 0, null, f.jan)
                .stream().map(TestQueueItemResponse::cardId).toList();

        // Limit 2 zaległych dziennie: dwa najpilniejsze pytania, bez nowego 50 i bez fiszki 60.
        assertThat(dueOnly).containsExactlyInAnyOrder(3L, 1L);
    }

    @Test
    void aDueOnlyTestSessionIsEmptyWhenNothingIsDueAndLoadsNothing() {
        Fixture f = new Fixture(100);
        f.card(50, true, 1);
        f.card(51, true, 1);
        f.card(52, true, 1);
        f.scheduled(52, 5, 1, -3); // termin za 3 dni

        assertThat(f.service.getTestQueue(null, null, null, 0, null, f.jan)).isEmpty();
        assertThat(f.loadedCardIds).isEmpty();
    }

    @Test
    void availableNewCardsCountsOnlyCardsWithoutHistory() {
        Fixture f = new Fixture(100);
        f.threeDueCards(false);
        f.card(8, false, 1);
        f.card(9, false, 1);

        assertThat(f.service.countAvailableNewCards(null, null, null, ReviewMode.FLASHCARD, f.jan)).isEqualTo(2);
    }

    // ------------------------------------------------------------------ limity z ustawień użytkownika

    private static UserSettings stored(Integer newCards, Integer newTests, Integer maxReviews) {
        UserSettings settings = new UserSettings(1L);
        settings.setNewCardsPerDay(newCards);
        settings.setNewTestQuestionsPerDay(newTests);
        settings.setMaxReviewsPerDay(maxReviews);
        return settings;
    }

    @Test
    void theUsersOwnReviewLimitReplacesTheDefault() {
        Fixture f = new Fixture(100);              // domyślnie 100 powtórek dziennie
        f.threeDueCards(false);
        f.stored = stored(null, null, 1);          // użytkownik ustawił 1

        assertThat(f.flashQueue()).containsExactly(3L);
        BacklogResponse backlog = f.service.getBacklog(null, null, null, ReviewMode.FLASHCARD, f.jan);
        assertThat(backlog.dailyLimit()).isEqualTo(1);
        assertThat(backlog.deferred()).isEqualTo(2L);
    }

    @Test
    void anUnsetReviewLimitFallsBackToTheServerDefault() {
        Fixture f = new Fixture(2);
        f.threeDueCards(false);
        f.stored = stored(5, 5, null);             // inne limity ustawione, ten nie

        assertThat(f.flashQueue()).containsExactly(3L, 1L);
        assertThat(f.service.getBacklog(null, null, null, ReviewMode.FLASHCARD, f.jan).dailyLimit()).isEqualTo(2);
    }

    @Test
    void theUsersOwnLimitOfNewFlashcardsReplacesTheDefault() {
        Fixture f = new Fixture(100, 20);          // domyślnie 20 nowych dziennie
        f.card(10, false, 1);
        f.card(20, false, 2);
        f.card(30, false, 3);
        f.stored = stored(2, null, null);

        assertThat(f.flashQueue()).containsExactly(10L, 20L);
    }

    @Test
    void testsUseTheirOwnNewQuestionLimit_notTheFlashcardOne() {
        Fixture f = new Fixture(100, 20);
        f.card(10, true, 1);
        f.card(20, true, 2);
        f.card(30, true, 3);
        f.card(11, false, 1);
        f.card(12, false, 2);
        f.card(13, false, 3);
        f.stored = stored(1, 2, null);             // fiszki: 1 nowa, testy: 2 nowe

        assertThat(f.testQueue()).hasSize(2);
        assertThat(f.flashQueue()).hasSize(1);
    }

    @Test
    void aZeroNewCardLimitOffersNoNewCardsButKeepsTheDueOnes() {
        Fixture f = new Fixture(100);
        f.threeDueCards(false);
        f.card(9, false, 1);
        f.stored = stored(0, null, null);

        assertThat(f.flashQueue()).containsExactly(3L, 1L, 2L);
    }

    // ---------------------------------------------------------------- zawieszone i trudne pytania

    @Test
    void suspendedCardIsNotInTheFlashcardQueue_andIsNotMistakenForANewCard() {
        Fixture f = new Fixture(100);
        f.threeDueCards(false);
        f.card(4, false, 1);
        f.scheduled(4, 10, 40, 30, 0, true);    // zawieszona (ma stan), zaległa od dawna
        f.card(5, false, 1);                    // nowa, bez stanu

        assertThat(f.flashQueue()).containsExactly(3L, 1L, 2L, 5L);
        assertThat(f.service.countAvailableNewCards(null, null, null, ReviewMode.FLASHCARD, f.jan)).isEqualTo(1);
    }

    @Test
    void suspendedCardIsNotInTheTestQueue_andNotInTheBacklog() {
        Fixture f = new Fixture(100);
        f.threeDueCards(true);
        f.card(4, true, 1);
        f.scheduled(4, 10, 40, 30, 0, true);

        assertThat(f.testQueue()).containsExactlyInAnyOrder(3L, 1L, 2L);
        assertThat(f.service.getBacklog(null, null, null, ReviewMode.TEST, f.jan).dueNow()).isEqualTo(3L);
        // To same pytania testowe, więc w Fiszkach nie ma nic do powtórki.
        assertThat(f.service.getBacklog(null, null, null, ReviewMode.FLASHCARD, f.jan).dueNow()).isEqualTo(0L);
    }

    @Test
    void suspendedCardDoesNotUseUpTheDailyReviewLimit() {
        Fixture f = new Fixture(2);
        f.threeDueCards(false);
        f.card(4, false, 1);
        f.scheduled(4, 1, 60, 50, 0, true);     // najbardziej zapomniana, ale zawieszona

        assertThat(f.flashQueue()).containsExactly(3L, 1L);
        assertThat(f.service.getBacklog(null, null, null, ReviewMode.FLASHCARD, f.jan).deferred()).isEqualTo(1L);
    }

    @Test
    void flashcardQueueCarriesLapsesAndTheDifficultFlag() {
        Fixture f = new Fixture(100);
        f.card(1, false, 1);
        f.card(2, false, 1);
        f.card(3, false, 1);
        f.scheduled(1, 10, 12, 2, 8, false);    // próg 8: trudne
        f.scheduled(2, 10, 20, 10, 7, false);   // jeszcze nie

        List<ReviewQueueItemResponse> queue = f.service.getQueue(null, null, null, null, null, f.jan);

        ReviewQueueItemResponse hard = queue.stream().filter(i -> i.cardId() == 1L).findFirst().orElseThrow();
        ReviewQueueItemResponse almost = queue.stream().filter(i -> i.cardId() == 2L).findFirst().orElseThrow();
        ReviewQueueItemResponse brandNew = queue.stream().filter(i -> i.cardId() == 3L).findFirst().orElseThrow();
        assertThat(hard.lapses()).isEqualTo(8);
        assertThat(hard.leech()).isTrue();
        assertThat(almost.lapses()).isEqualTo(7);
        assertThat(almost.leech()).isFalse();
        assertThat(brandNew.lapses()).isZero();
        assertThat(brandNew.leech()).isFalse();
    }

    @Test
    void onlyTheCardsOfTheSessionAreLoadedInFull_notTheWholePool() {
        Fixture f = new Fixture(100, 20);
        for (long id = 1; id <= 300; id++) {
            f.card(id, false, (int) (id / 50));
        }

        List<Long> queue = f.service.getQueue(null, null, null, null, null, f.jan)
                .stream().map(ReviewQueueItemResponse::cardId).toList();

        assertThat(queue).hasSize(20);
        // z 300 dostępnych kart pełne treści ładuje się tylko dla 20 z sesji, w kolejności sesji
        assertThat(f.loadedCardIds).hasSize(20).containsExactlyElementsOf(queue);
    }

    @Test
    void testQueueLoadsFullQuestionsOnlyForTheSession() {
        Fixture f = new Fixture(100);
        f.threeDueCards(true);
        for (long id = 10; id < 200; id++) {
            f.card(id, true, 1);
        }

        List<TestQueueItemResponse> queue = f.service.getTestQueue(null, null, null, null, null, f.jan);

        // 3 zaległe + dzienny limit nowych pytań testowych (40), z 190 dostępnych nowych
        assertThat(queue).hasSize(3 + 40);
        assertThat(f.loadedCardIds).hasSize(43).containsExactlyInAnyOrderElementsOf(
                queue.stream().map(TestQueueItemResponse::cardId).toList());
    }

    @Test
    void backlogAndNewCardCountsLoadNoFullCardsAtAll() {
        Fixture f = new Fixture(2);
        f.threeDueCards(true);
        f.card(50, true, 1);

        f.service.getBacklog(null, null, null, ReviewMode.TEST, f.jan);
        f.service.getBacklog(null, null, null, ReviewMode.FLASHCARD, f.jan);
        f.service.countAvailableNewCards(null, null, null, ReviewMode.FLASHCARD, f.jan);
        f.service.countAvailableNewCards(null, null, null, ReviewMode.TEST, f.jan);

        assertThat(f.loadedCardIds).isEmpty();
    }

    @Test
    void anEmptyQueueDoesNotAskTheDatabaseForCards() {
        Fixture f = new Fixture(100);

        assertThat(f.service.getQueue(null, null, null, null, null, f.jan)).isEmpty();
        assertThat(f.loadedCardIds).isEmpty();
    }

    @Test
    void aCardWithoutAChapterSortsFirstAmongNewCards() {
        Fixture f = new Fixture(100, 3);
        Card noChapter = f.card(7, false, 5);
        noChapter.setChapter(null);
        f.card(2, false, 1);
        f.card(1, false, 2);

        List<Long> queue = f.service.getQueue(null, null, null, null, null, f.jan)
                .stream().map(ReviewQueueItemResponse::cardId).toList();

        assertThat(queue).containsExactly(7L, 2L, 1L);
    }

    // ---------------------------------------------------------------- Fiszki i Testy osobno

    @Test
    void flashcardQueueNeverContainsTestQuestions_andTheTestQueueNeverContainsFlashcards() {
        Fixture f = new Fixture(100);
        f.card(1, true, 1);
        f.card(2, false, 1);
        f.card(3, true, 2);
        f.card(4, false, 2);

        assertThat(f.flashQueue()).containsExactly(2L, 4L);
        assertThat(f.testQueue()).containsExactlyInAnyOrder(1L, 3L);
    }

    @Test
    void newCardCountsFollowTheMode() {
        Fixture f = new Fixture(100);
        f.card(1, true, 1);
        f.card(2, false, 1);
        f.card(3, false, 2);

        assertThat(f.service.countAvailableNewCards(null, null, null, ReviewMode.FLASHCARD, f.jan)).isEqualTo(2);
        assertThat(f.service.countAvailableNewCards(null, null, null, ReviewMode.TEST, f.jan)).isEqualTo(1);
    }

    @Test
    void dueBacklogOfEachModeCountsOnlyItsOwnCards() {
        Fixture f = new Fixture(100);
        f.card(1, true, 1);
        f.card(2, false, 1);
        f.scheduled(1, 5, 10, 5);
        f.scheduled(2, 5, 10, 5);

        assertThat(f.service.getBacklog(null, null, null, ReviewMode.FLASHCARD, f.jan).dueNow()).isEqualTo(1L);
        assertThat(f.service.getBacklog(null, null, null, ReviewMode.TEST, f.jan).dueNow()).isEqualTo(1L);
    }

    // ---------------------------------------------------------------- fiszki powiązane z pytaniami

    @Test
    void linkedFlashcardIsHiddenUntilTheUserConfidentlyMissesItsQuestion() {
        Fixture f = new Fixture(100);
        f.card(1, true, 1);                     // pytanie testowe
        f.linkedFlashcard(2, 1, 1);             // jego fiszka
        f.card(3, false, 1);                    // zwykła fiszka

        assertThat(f.flashQueue()).containsExactly(3L);
        assertThat(f.service.countAvailableNewCards(null, null, null, ReviewMode.FLASHCARD, f.jan)).isEqualTo(1);
        assertThat(f.service.getBacklog(null, null, null, ReviewMode.FLASHCARD, f.jan).dueNow()).isEqualTo(0L);
    }

    @Test
    void linkedFlashcardAppearsAsANewCardAfterAConfidentMistake() {
        Fixture f = new Fixture(100);
        f.card(1, true, 1);
        f.linkedFlashcard(2, 1, 1);
        f.card(3, false, 2);
        f.confidentMistakes.add(1L);

        assertThat(f.flashQueue()).containsExactly(2L, 3L);
        assertThat(f.service.countAvailableNewCards(null, null, null, ReviewMode.FLASHCARD, f.jan)).isEqualTo(2);
    }

    @Test
    void aMistakeInADifferentQuestionDoesNotUnlockTheFlashcard() {
        Fixture f = new Fixture(100);
        f.card(1, true, 1);
        f.card(5, true, 1);
        f.linkedFlashcard(2, 1, 1);
        f.confidentMistakes.add(5L);

        assertThat(f.flashQueue()).isEmpty();
    }

    @Test
    void anUnlockedFlashcardKeepsItsOwnScheduleAndComesBackWhenDue() {
        Fixture f = new Fixture(100);
        f.card(1, true, 1);
        f.linkedFlashcard(2, 1, 1);
        f.confidentMistakes.add(1L);
        f.scheduled(2, 3, 6, 2);

        assertThat(f.flashQueue()).containsExactly(2L);
        assertThat(f.service.getBacklog(null, null, null, ReviewMode.FLASHCARD, f.jan).dueNow()).isEqualTo(1L);
    }

    @Test
    void unlockedLookupIsSkippedWhenNoFlashcardIsLinked() {
        Fixture f = new Fixture(100);
        f.card(1, true, 1);
        f.card(2, false, 1);

        f.flashQueue();
        f.service.getBacklog(null, null, null, ReviewMode.FLASHCARD, f.jan);

        assertThat(f.confidentMistakeLookups).isZero();
    }

    @Test
    void theTestQueueIgnoresLinksAndUnlocks() {
        Fixture f = new Fixture(100);
        f.card(1, true, 1);
        f.linkedFlashcard(2, 1, 1);

        assertThat(f.testQueue()).containsExactly(1L);
        assertThat(f.confidentMistakeLookups).isZero();
    }
}
