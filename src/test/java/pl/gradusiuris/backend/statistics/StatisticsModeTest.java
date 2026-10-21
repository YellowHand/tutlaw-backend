package pl.gradusiuris.backend.statistics;

import org.junit.jupiter.api.Test;
import pl.gradusiuris.backend.common.GroupCount;
import pl.gradusiuris.backend.flashcard.FlashcardAdditionRepository;
import pl.gradusiuris.backend.flashcard.FlashcardUnlockService;
import pl.gradusiuris.backend.legal.LegalAct;
import pl.gradusiuris.backend.legal.LegalActRepository;
import pl.gradusiuris.backend.pool.Pool;
import pl.gradusiuris.backend.pool.SourceFilter;
import pl.gradusiuris.backend.question.Question;
import pl.gradusiuris.backend.question.QuestionRepository;
import pl.gradusiuris.backend.review.ReviewLogRepository;
import pl.gradusiuris.backend.review.ReviewMode;
import pl.gradusiuris.backend.review.UserQuestionStateRepository;
import pl.gradusiuris.backend.review.fsrs.FsrsEngine;
import pl.gradusiuris.backend.review.fsrs.FsrsWeights;
import pl.gradusiuris.backend.statistics.dto.ActMasteryResponse;
import pl.gradusiuris.backend.statistics.dto.OverviewResponse;
import pl.gradusiuris.backend.statistics.dto.WeakQuestionResponse;
import pl.gradusiuris.backend.testsupport.FakeRepos;
import pl.gradusiuris.backend.testsupport.MutableClock;
import pl.gradusiuris.backend.testsupport.Pools;
import pl.gradusiuris.backend.testsupport.QuestionRefs;
import pl.gradusiuris.backend.user.CurrentUserResolver;
import pl.gradusiuris.backend.user.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Przegląd, opanowanie aktów i najsłabsze pytania w trybach Fiszki / Testy / razem, na prawdziwym StatisticsService
 * z repozytoriami "w pamięci". Pilnuje, że tryb wybiera właściwą pulę (fiszki bez filtra źródeł) i właściwe stany.
 */
class StatisticsModeTest {

    private static GroupCount group(long id, long total) {
        return new GroupCount() {
            public Long getGroupId() { return id; }
            public Long getTotal() { return total; }
        };
    }

    private static UserQuestionStateRepository.StartedSummary summary(
            long questionId, long actId, ReviewMode mode, double stability, LocalDateTime last) {
        return new UserQuestionStateRepository.StartedSummary() {
            public Long getQuestionId() { return questionId; }
            public Long getLegalActId() { return actId; }
            public Double getStability() { return stability; }
            public Integer getLapses() { return 2; }
            public LocalDateTime getLastReviewAt() { return last; }
            public ReviewMode getMode() { return mode; }
        };
    }

    private static final class Fixture {
        final List<Question> questions = new ArrayList<>();
        final List<Long> confidentMistakes = new ArrayList<>();
        final List<Long> manualAdditions = new ArrayList<>();
        /** Które źródła dostały zapytania o zliczanie: "TRYB:źródła". */
        final List<String> sourcesSeen = new ArrayList<>();
        final List<String> activityLookups = new ArrayList<>();
        final List<UserQuestionStateRepository.StartedSummary> summaries = new ArrayList<>();
        final List<LegalAct> acts = new ArrayList<>();
        Pool pool = Pools.of(new SourceFilter(true, false, false), 1, 2);
        final StatisticsService service;
        final org.springframework.security.core.Authentication jan = FakeRepos.authentication("jan@gmail.com");

        Fixture() {
            User user = new User();
            user.setId(1L);
            user.setPrivateEmail("jan@gmail.com");
            QuestionRepository questionRepository = FakeRepos.proxy(QuestionRepository.class, (method, args) -> {
                switch (method) {
                    case "findRefsInPool":
                        sourcesSeen.add("refs:" + sorted(args[1]));
                        return QuestionRefs.inPool(questions, (Collection<Long>) args[0], (Collection<Long>) args[1]);
                    case "countByAct":
                        sourcesSeen.add("countByAct:" + sorted(args[1]));
                        return counts(questions, (Collection<Long>) args[0], (Collection<Long>) args[1], false);
                    case "countUnlockedFlashcardsByAct":
                        sourcesSeen.add("unlocked:" + sorted(args[2]));
                        return counts(questions, (Collection<Long>) args[1], (Collection<Long>) args[2], true);
                    case "findAllById":
                        return questions.stream().filter(q -> ((Iterable<Long>) args[0]).spliterator() != null
                                && contains((Iterable<Long>) args[0], q.getId())).toList();
                    default:
                        throw new UnsupportedOperationException("QuestionRepository." + method);
                }
            });
            UserQuestionStateRepository states = FakeRepos.proxy(UserQuestionStateRepository.class, (method, args) -> {
                switch (method) {
                    case "countStartedByAct":
                        sourcesSeen.add("started:" + args[1] + ":" + sorted(args[4]));
                        return List.of();
                    case "findDueUntil":
                        return List.of();
                    case "findStartedSummaries":
                        sourcesSeen.add("summaries:" + args[1]);
                        return summaries.stream().filter(s -> s.getMode() == args[1]).toList();
                    default:
                        throw new UnsupportedOperationException("UserQuestionStateRepository." + method);
                }
            });
            ReviewLogRepository logs = FakeRepos.proxy(ReviewLogRepository.class, (method, args) -> {
                switch (method) {
                    case "countRecalls":
                    case "countRecallsInAct":
                        return 0L;
                    case "findConfidentMistakeQuestionIds":
                        return new ArrayList<>(confidentMistakes);
                    case "findReviewTimesSince":
                        activityLookups.add("all");
                        return List.of();
                    case "findReviewTimesSinceByMode":
                        activityLookups.add(String.valueOf(args[1]));
                        return List.of();
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
            LegalActRepository actRepository = FakeRepos.proxy(LegalActRepository.class, (method, args) -> {
                if (method.equals("findAllWithFieldByIdIn")) {
                    return new ArrayList<>(acts);
                }
                throw new UnsupportedOperationException("LegalActRepository." + method);
            });
            List<User> users = new ArrayList<>(List.of(user));
            service = new StatisticsService(
                    questionRepository, actRepository, states, logs, new CurrentUserResolver(FakeRepos.users(users)),
                    new FsrsEngine(FsrsWeights.defaults(), 0.9), MutableClock.at("2026-10-12T08:00:00Z"),
                    Pools.holding(() -> pool), new FlashcardUnlockService(logs, additions));
        }

        private static boolean contains(Iterable<Long> ids, Long id) {
            for (Long candidate : ids) {
                if (candidate.equals(id)) return true;
            }
            return false;
        }

        private static String sorted(Object ids) {
            return ((Collection<Long>) ids).stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
        }

        private static List<GroupCount> counts(List<Question> all, Collection<Long> actIds, Collection<Long> sourceIds,
                                               boolean onlyFlashcards) {
            return all.stream()
                    .filter(q -> !q.isHidden() && actIds.contains(q.getLegalAct().getId())
                            && sourceIds.contains(q.getSource().getId()) && (!onlyFlashcards || !q.isFlashcardHidden()))
                    .collect(Collectors.groupingBy(q -> q.getLegalAct().getId(), Collectors.counting()))
                    .entrySet().stream().map(e -> group(e.getKey(), e.getValue())).toList();
        }

        Question add(long id, long actId, long sourceId) {
            Question q = QuestionRefs.question(id, actId, 1, sourceId);
            questions.add(q);
            return q;
        }

        /** Dwa pytania we własnym arkuszu, jedno w arkuszu innej aplikacji, jedno autorskie (akt 1). */
        Fixture withMaterial() {
            add(1, 1, QuestionRefs.OWN_SHEET);
            add(2, 1, QuestionRefs.OWN_SHEET);
            add(3, 1, QuestionRefs.OTHER_SHEET);
            add(4, 2, QuestionRefs.AUTHORED);
            return this;
        }
    }

    @Test
    void testMode_countsQuestionsOfTheAllowedSourcesOnly() {
        Fixture f = new Fixture().withMaterial(); // filtr: tylko własne arkusze

        OverviewResponse overview = f.service.getOverview(null, ReviewMode.TEST, f.jan);

        assertThat(overview.totalQuestions()).isEqualTo(2); // pytania 1 i 2
    }

    @Test
    void flashcardMode_countsOnlyUnlockedQuestions_andIgnoresTheSourceFilter() {
        Fixture f = new Fixture().withMaterial();
        f.confidentMistakes.add(3L);  // pytanie z arkusza innej aplikacji, odblokowane pewnym błędem
        f.manualAdditions.add(4L);    // pytanie autorskie dodane ręcznie

        OverviewResponse overview = f.service.getOverview(null, ReviewMode.FLASHCARD, f.jan);

        assertThat(overview.totalQuestions()).isEqualTo(2); // 3 i 4, mimo że filtr wycina te źródła w testach
    }

    @Test
    void flashcardMode_hasNothingUntilSomethingIsUnlocked() {
        Fixture f = new Fixture().withMaterial();

        assertThat(f.service.getOverview(null, ReviewMode.FLASHCARD, f.jan).totalQuestions()).isZero();
    }

    @Test
    void flashcardMode_doesNotCountUnlockedQuestionsWhoseFlashcardIsHidden() {
        Fixture f = new Fixture().withMaterial();
        f.questions.get(0).setFlashcardHidden(true);
        f.confidentMistakes.add(1L);
        f.confidentMistakes.add(2L);

        assertThat(f.service.getOverview(null, ReviewMode.FLASHCARD, f.jan).totalQuestions()).isEqualTo(1);
    }

    @Test
    void noMode_sumsBothModes() {
        Fixture f = new Fixture().withMaterial();
        f.confidentMistakes.add(3L);

        OverviewResponse overview = f.service.getOverview(null, null, f.jan);

        assertThat(overview.totalQuestions()).isEqualTo(3); // 2 w testach + 1 odblokowana fiszka
    }

    @Test
    void anActFilter_narrowsTheCountsToThatAct() {
        Fixture f = new Fixture().withMaterial();
        f.pool = Pools.of(SourceFilter.ALL, 1, 2);

        assertThat(f.service.getOverview(1L, ReviewMode.TEST, f.jan).totalQuestions()).isEqualTo(3);
        assertThat(f.service.getOverview(2L, ReviewMode.TEST, f.jan).totalQuestions()).isEqualTo(1);
    }

    @Test
    void theModeChoosesThePoolSources_testsFilteredAndFlashcardsAll() {
        Fixture f = new Fixture().withMaterial();

        f.service.getMasteryByAct(ReviewMode.TEST, f.jan);
        f.service.getMasteryByAct(ReviewMode.FLASHCARD, f.jan);

        assertThat(f.sourcesSeen).contains("countByAct:1", "started:TEST:1", "unlocked:1,2,3", "started:FLASHCARD:1,2,3");
    }

    // ---------------------------------------------------------------- opanowanie aktów

    @Test
    void masteryByAct_listsEveryActOfThePool_withCountsPerMode() {
        Fixture f = new Fixture().withMaterial();
        f.pool = Pools.of(SourceFilter.ALL, 1, 2);
        f.acts.add(QuestionRefs.act(2, 1));
        f.acts.add(QuestionRefs.act(1, 1));

        List<ActMasteryResponse> mastery = f.service.getMasteryByAct(ReviewMode.TEST, f.jan);

        assertThat(mastery).extracting(ActMasteryResponse::actId).containsExactlyInAnyOrder(1L, 2L);
        assertThat(mastery).filteredOn(m -> m.actId() == 1L).first().satisfies(m -> {
            assertThat(m.totalQuestions()).isEqualTo(3);
            assertThat(m.matureQuestions()).isZero();
            assertThat(m.masteryPercentage()).isZero();
        });
    }

    // ---------------------------------------------------------------- najsłabsze pytania

    @Test
    void weakQuestions_theSameQuestionInBothModesAppearsOnce_withTheWorseScore() {
        Fixture f = new Fixture().withMaterial();
        f.pool = Pools.of(SourceFilter.ALL, 1, 2);
        LocalDateTime longAgo = LocalDateTime.of(2026, 9, 1, 10, 0);
        LocalDateTime yesterday = LocalDateTime.of(2026, 10, 11, 10, 0);
        f.summaries.add(summary(1, 1, ReviewMode.TEST, 5.0, yesterday));
        f.summaries.add(summary(1, 1, ReviewMode.FLASHCARD, 5.0, longAgo)); // fiszka dawno niewidziana: gorsza
        f.summaries.add(summary(2, 1, ReviewMode.TEST, 5.0, yesterday));

        List<WeakQuestionResponse> weak = f.service.getWeakQuestions(null, null, 10, f.jan);

        assertThat(weak).extracting(WeakQuestionResponse::questionId).containsExactly(1L, 2L);
        assertThat(weak.get(0).mode()).isEqualTo(ReviewMode.FLASHCARD);
        assertThat(weak.get(0).currentRetrievability()).isLessThan(weak.get(1).currentRetrievability());
    }

    @Test
    void weakQuestions_withAMode_looksAtThatModeOnly() {
        Fixture f = new Fixture().withMaterial();
        f.summaries.add(summary(1, 1, ReviewMode.TEST, 5.0, LocalDateTime.of(2026, 10, 1, 10, 0)));
        f.summaries.add(summary(2, 1, ReviewMode.FLASHCARD, 5.0, LocalDateTime.of(2026, 10, 1, 10, 0)));

        List<WeakQuestionResponse> weak = f.service.getWeakQuestions(null, ReviewMode.TEST, 10, f.jan);

        assertThat(weak).extracting(WeakQuestionResponse::questionId).containsExactly(1L);
    }

    @Test
    void weakQuestions_anActFilterAndTheLimitApply() {
        Fixture f = new Fixture().withMaterial();
        LocalDateTime last = LocalDateTime.of(2026, 10, 1, 10, 0);
        f.summaries.add(summary(1, 1, ReviewMode.TEST, 5.0, last));
        f.summaries.add(summary(2, 1, ReviewMode.TEST, 6.0, last));
        f.summaries.add(summary(4, 2, ReviewMode.TEST, 4.0, last));

        assertThat(f.service.getWeakQuestions(1L, ReviewMode.TEST, 10, f.jan)).hasSize(2);
        assertThat(f.service.getWeakQuestions(1L, ReviewMode.TEST, 1, f.jan)).hasSize(1);
        assertThat(f.service.getWeakQuestions(2L, ReviewMode.TEST, 10, f.jan))
                .extracting(WeakQuestionResponse::questionId).containsExactly(4L);
    }

    // ---------------------------------------------------------------- aktywność

    @Test
    void activity_withoutMode_countsEverything_andWithModeOnlyThatMode() {
        Fixture f = new Fixture();

        f.service.getActivity(28, null, f.jan);
        f.service.getActivity(28, ReviewMode.FLASHCARD, f.jan);
        f.service.getActivity(28, ReviewMode.TEST, f.jan);

        assertThat(f.activityLookups).containsExactly("all", "FLASHCARD", "TEST");
    }
}
