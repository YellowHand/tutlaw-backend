package pl.gradusiuris.backend.review;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import pl.gradusiuris.backend.pool.Pool;
import pl.gradusiuris.backend.pool.SourceFilter;
import pl.gradusiuris.backend.question.Question;
import pl.gradusiuris.backend.question.QuestionRepository;
import pl.gradusiuris.backend.review.dto.MistakesCountResponse;
import pl.gradusiuris.backend.review.dto.TestQueueItemResponse;
import pl.gradusiuris.backend.testsupport.MutableClock;
import pl.gradusiuris.backend.testsupport.Pools;
import pl.gradusiuris.backend.testsupport.QuestionRefs;
import pl.gradusiuris.backend.user.CurrentUserResolver;
import pl.gradusiuris.backend.user.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MistakesServiceTest {

    // 2026-10-12 12:00 czasu warszawskiego
    private final MutableClock clock = MutableClock.at("2026-10-12T10:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 12, 0);

    @Mock private ReviewLogRepository reviewLogRepository;
    @Mock private QuestionRepository questionRepository;
    @Mock private CurrentUserResolver currentUserResolver;
    /** Pula użytkownika; testy filtra źródeł ją podmieniają. */
    private Pool pool = Pools.all();
    private final Authentication authentication = mock(Authentication.class);

    private MistakesService service;

    @BeforeEach
    void setUp() {
        service = new MistakesService(
                reviewLogRepository, questionRepository, currentUserResolver, clock, Pools.holding(() -> pool));
        User user = new User();
        user.setId(5L);
        when(currentUserResolver.resolve(authentication)).thenReturn(user);
    }

    private static Question question(long id) {
        Question question = QuestionRefs.question(id);
        question.setBody("Pytanie " + id);
        return question;
    }

    private static ReviewLogRepository.TestAnswerRow answer(long questionId, boolean correct, int daysAgo) {
        return new ReviewLogRepository.TestAnswerRow() {
            public Long getQuestionId() { return questionId; }
            public Boolean getWasCorrect() { return correct; }
            public LocalDateTime getReviewedAt() { return NOW.minusDays(daysAgo); }
        };
    }

    /** Odpowiedzi w kolejności, w jakiej zwraca je repozytorium: od najnowszej. */
    private void answers(ReviewLogRepository.TestAnswerRow... rows) {
        when(reviewLogRepository.findTestAnswersSince(
                eq(5L), ArgumentMatchers.<LocalDateTime>any(), ArgumentMatchers.<Pageable>any()))
                .thenReturn(List.of(rows));
    }

    /** Pytania, które zapytanie o dostępność uzna za ćwiczalne (bez zawieszonych, ukrytych i spoza aktów puli). */
    private void practicable(Question... questions) {
        when(questionRepository.findPracticeQuestions(
                ArgumentMatchers.<Collection<Long>>any(), eq(5L), ArgumentMatchers.<Collection<Long>>any()))
                .thenReturn(List.of(questions));
    }

    private List<Long> idsAskedAboutAvailability() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(questionRepository).findPracticeQuestions(
                captor.capture(), eq(5L), ArgumentMatchers.<Collection<Long>>any());
        return new ArrayList<>(captor.getValue());
    }

    // ---------------------------------------------------------------- co jest błędem

    @Test
    void count_includesQuestionsWhoseLatestAnswerWasWrong() {
        answers(answer(1, false, 1), answer(2, false, 2));
        practicable(question(1), question(2));

        MistakesCountResponse response = service.count(authentication);

        assertThat(response.count()).isEqualTo(2);
        assertThat(response.lookbackDays()).isEqualTo(30);
        assertThat(response.sessionSize()).isEqualTo(50);
    }

    @Test
    void aWrongAnswerFollowedByACorrectOneIsNoLongerAMistake() {
        // pytanie 1: najnowsza (wczoraj) poprawna, wcześniejsza błędna; pytanie 2: tylko błędna
        answers(answer(1, true, 1), answer(2, false, 2), answer(1, false, 5));
        practicable(question(2));

        service.count(authentication);

        assertThat(idsAskedAboutAvailability()).containsExactly(2L);
    }

    @Test
    void aCorrectAnswerFollowedByAWrongOneIsAMistake() {
        // pytanie 1: najnowsza błędna, starsza poprawna
        answers(answer(1, false, 1), answer(1, true, 6));
        practicable(question(1));

        assertThat(service.count(authentication).count()).isEqualTo(1);
        assertThat(idsAskedAboutAvailability()).containsExactly(1L);
    }

    @Test
    void anUnsureButCorrectAnswerIsNotAMistake() {
        // "poprawna, ale niepewna" jest w dzienniku wasCorrect = true, więc nie trafia do błędów
        answers(answer(1, true, 1), answer(2, true, 2));

        assertThat(service.count(authentication).count()).isZero();
    }

    @Test
    void mistakesAreListedFromTheFreshestOne() {
        answers(answer(3, false, 1), answer(1, false, 4), answer(2, false, 9));
        practicable(question(1), question(2), question(3));

        service.count(authentication);

        assertThat(idsAskedAboutAvailability()).containsExactly(3L, 1L, 2L);
    }

    // ---------------------------------------------------------------- dostępność

    @Test
    void onlyQuestionsThatCanStillBePracticedSurvive() {
        // pytania 1 i 3 są zawieszone albo spoza puli: zapytanie o dostępność ich nie zwraca
        answers(answer(1, false, 1), answer(2, false, 2), answer(3, false, 3));
        practicable(question(2));

        MistakesCountResponse response = service.count(authentication);

        assertThat(response.count()).isEqualTo(1);
        assertThat(service.queue(authentication)).extracting(TestQueueItemResponse::questionId).containsExactly(2L);
    }

    @Test
    void withoutMistakesNothingIsLookedUp() {
        answers();

        assertThat(service.count(authentication).count()).isZero();
        assertThat(service.queue(authentication)).isEmpty();

        verify(questionRepository, never()).findPracticeQuestions(
                ArgumentMatchers.<Collection<Long>>any(), ArgumentMatchers.<Long>any(), ArgumentMatchers.<Collection<Long>>any());
    }

    @Test
    void theLookbackWindowIsThirtyDaysBack() {
        answers();

        service.count(authentication);

        ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(reviewLogRepository).findTestAnswersSince(eq(5L), since.capture(), ArgumentMatchers.<Pageable>any());
        assertThat(since.getValue()).isEqualTo(NOW.minusDays(30));
    }

    // ---------------------------------------------------------------- sesja

    @Test
    void sessionHoldsAtMostFiftyQuestions_butTheCountShowsAllEligible() {
        List<ReviewLogRepository.TestAnswerRow> rows = new ArrayList<>();
        List<Question> questions = new ArrayList<>();
        for (long id = 1; id <= 60; id++) {
            rows.add(answer(id, false, 1));
            questions.add(question(id));
        }
        answers(rows.toArray(new ReviewLogRepository.TestAnswerRow[0]));
        practicable(questions.toArray(new Question[0]));

        assertThat(service.count(authentication).count()).isEqualTo(60);

        List<TestQueueItemResponse> queue = service.queue(authentication);
        assertThat(queue).hasSize(50);
        // do sesji wchodzi 50 najświeższych (tu: pierwsze 50 z listy), nie dowolne
        assertThat(queue).extracting(TestQueueItemResponse::questionId).doesNotHaveDuplicates()
                .allMatch(id -> id <= 50);
    }

    @Test
    void queueItemsCarryTheOptionsButNotTheCorrectAnswer() {
        answers(answer(1, false, 1));
        practicable(question(1));

        List<TestQueueItemResponse> queue = service.queue(authentication);

        assertThat(queue).hasSize(1);
        TestQueueItemResponse item = queue.get(0);
        assertThat(item.questionId()).isEqualTo(1L);
        assertThat(item.body()).isEqualTo("Pytanie 1");
        assertThat(item.optionA()).isEqualTo("a");
        assertThat(item.optionB()).isEqualTo("b");
        assertThat(item.optionC()).isEqualTo("c");
    }

    @Test
    void theNumberOfScannedQuestionsIsCapped() {
        List<ReviewLogRepository.TestAnswerRow> rows = new ArrayList<>();
        for (long id = 1; id <= 1_200; id++) {
            rows.add(answer(id, false, 1));
        }
        answers(rows.toArray(new ReviewLogRepository.TestAnswerRow[0]));
        practicable();

        service.count(authentication);

        assertThat(idsAskedAboutAvailability()).hasSize(1_000);
    }

    // ---------------------------------------------------------------- pula: akty i filtr źródeł

    private static Question questionFrom(long id, long sourceId) {
        Question question = QuestionRefs.question(id, 1, 1, sourceId);
        question.setBody("Pytanie " + id);
        return question;
    }

    @Test
    void mistakesFromSourcesOutsideTheFilterAreNeitherCountedNorQueued() {
        answers(answer(1, false, 1), answer(2, false, 2), answer(3, false, 3));
        practicable(questionFrom(1, QuestionRefs.OWN_SHEET), questionFrom(2, QuestionRefs.OTHER_SHEET),
                questionFrom(3, QuestionRefs.AUTHORED));
        pool = Pools.of(new SourceFilter(true, false, true), 1, 2);

        assertThat(service.count(authentication).count()).isEqualTo(2);
        assertThat(service.queue(authentication)).extracting(TestQueueItemResponse::questionId)
                .containsExactlyInAnyOrder(1L, 3L);
    }

    @Test
    void aMistakeInAFilteredOutSourceComesBackWhenTheFilterIsWidened() {
        answers(answer(1, false, 1));
        practicable(questionFrom(1, QuestionRefs.OTHER_SHEET));
        pool = Pools.of(new SourceFilter(true, false, false), 1, 2);
        assertThat(service.count(authentication).count()).isZero();

        pool = Pools.of(SourceFilter.ALL, 1, 2);
        assertThat(service.count(authentication).count()).isEqualTo(1);
    }

    @Test
    void theAvailabilityQueryGetsTheActsOfTheUsersPool() {
        answers(answer(1, false, 1));
        practicable(question(1));
        pool = Pools.narrowedTo(Pools.all(), 2);

        service.count(authentication);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> acts = ArgumentCaptor.forClass(Collection.class);
        verify(questionRepository).findPracticeQuestions(
                ArgumentMatchers.<Collection<Long>>any(), eq(5L), acts.capture());
        assertThat(acts.getValue()).containsExactly(2L);
    }
}
