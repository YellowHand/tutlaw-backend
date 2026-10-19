package pl.tutlaw.backend.exam;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import pl.tutlaw.backend.exam.dto.ExamAttemptResponse;
import pl.tutlaw.backend.exam.dto.ExamResultResponse;
import pl.tutlaw.backend.exam.dto.ExamStartResponse;
import pl.tutlaw.backend.exam.dto.ExamSubmitRequest;
import pl.tutlaw.backend.pool.Pool;
import pl.tutlaw.backend.pool.SourceFilter;
import pl.tutlaw.backend.question.CorrectOption;
import pl.tutlaw.backend.question.Question;
import pl.tutlaw.backend.question.QuestionRepository;
import pl.tutlaw.backend.review.ReviewMode;
import pl.tutlaw.backend.review.UserQuestionStateRepository;
import pl.tutlaw.backend.review.dto.TestQueueItemResponse;
import pl.tutlaw.backend.testsupport.MutableClock;
import pl.tutlaw.backend.testsupport.Pools;
import pl.tutlaw.backend.testsupport.QuestionRefs;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamServiceTest {

    // 2026-10-12 12:00 czasu warszawskiego
    private final MutableClock clock = MutableClock.at("2026-10-12T10:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 12, 0);

    @Mock private QuestionRepository questionRepository;
    @Mock private UserQuestionStateRepository stateRepository;
    @Mock private ExamAttemptRepository attemptRepository;
    @Mock private CurrentUserResolver currentUserResolver;
    private final Authentication authentication = mock(Authentication.class);

    private Pool pool = Pools.all();
    private ExamService service;

    @BeforeEach
    void setUp() {
        service = new ExamService(questionRepository, stateRepository, attemptRepository, currentUserResolver, clock,
                Pools.holding(() -> pool));
        User user = new User();
        user.setId(5L);
        when(currentUserResolver.resolve(authentication)).thenReturn(user);
    }

    // ---------------------------------------------------------------- dane

    /** Pytanie w akcie 1, własny arkusz, poprawna odpowiedź A. */
    private static Question question(long id) {
        Question q = QuestionRefs.question(id);
        q.getSource().setName("Egzamin 2024");
        return q;
    }

    private static Question question(long id, CorrectOption correct, String sourceName) {
        Question q = question(id);
        q.setCorrectOption(correct);
        q.getSource().setName(sourceName);
        return q;
    }

    private static Question inSource(long id, long sourceId) {
        return QuestionRefs.question(id, 1, 1, sourceId);
    }

    private static List<Question> questions(long from, long to) {
        List<Question> list = new ArrayList<>();
        for (long id = from; id <= to; id++) {
            list.add(question(id));
        }
        return list;
    }

    private static ExamSubmitRequest.Answer answer(long questionId, CorrectOption selected) {
        return new ExamSubmitRequest.Answer(questionId, selected);
    }

    private static ExamSubmitRequest submitRequest(ExamSubmitRequest.Answer... answers) {
        return new ExamSubmitRequest(150, 3_600, false, List.of(answers));
    }

    /** Odpowiedzi do pytań 1..total, z czego pierwsze {@code correct} poprawne (A), a reszta błędna (B). */
    private static ExamSubmitRequest.Answer[] answers(int total, int correct) {
        ExamSubmitRequest.Answer[] answers = new ExamSubmitRequest.Answer[total];
        for (int i = 0; i < total; i++) {
            answers[i] = answer(i + 1L, i < correct ? CorrectOption.A : CorrectOption.B);
        }
        return answers;
    }

    /** Pytania, które baza zwróci do oceny (po numerach). */
    private void gradable(Question... list) {
        when(questionRepository.findForGrading(ArgumentMatchers.<Collection<Long>>any())).thenReturn(List.of(list));
    }

    /** Pula i pełne pytania wylosowanych numerów. Lenient, bo nie każdy test dociera do wczytywania. */
    private void givenPool(List<Question> all, List<Long> suspendedInTests) {
        when(questionRepository.findRefsInPool(ArgumentMatchers.<Collection<Long>>any(), ArgumentMatchers.<Collection<Long>>any()))
                .thenAnswer(inv -> QuestionRefs.inPool(all, inv.getArgument(0), inv.getArgument(1)));
        lenient().when(questionRepository.findAllWithRelationsByIdIn(any()))
                .thenAnswer(inv -> QuestionRefs.byIds(all, inv.getArgument(0)));
        when(stateRepository.findSuspendedQuestionIds(5L, ReviewMode.TEST)).thenReturn(suspendedInTests);
    }

    private ExamAttempt savedAttempt() {
        ArgumentCaptor<ExamAttempt> captor = ArgumentCaptor.forClass(ExamAttempt.class);
        verify(attemptRepository).save(captor.capture());
        return captor.getValue();
    }

    // ---------------------------------------------------------------- losowanie

    @Test
    void start_drawsExactly150DistinctQuestions_andTellsThePassThreshold() {
        givenPool(questions(1, 400), List.of());

        ExamStartResponse response = service.start(authentication);

        assertThat(response.questions()).hasSize(150);
        assertThat(response.requestedCount()).isEqualTo(150);
        assertThat(response.passPoints()).isEqualTo(100);
        assertThat(response.questions().stream().map(TestQueueItemResponse::questionId).collect(Collectors.toSet()))
                .hasSize(150)
                .allMatch(id -> id >= 1 && id <= 400);
        assertThat(response.scopeLabel()).isEqualTo("Cała pula");
    }

    @Test
    void start_loadsFullQuestionsOnlyForTheDrawnOnes_inTheDrawnOrder() {
        givenPool(questions(1, 600), List.of());

        ExamStartResponse response = service.start(authentication);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(questionRepository).findAllWithRelationsByIdIn(captor.capture());
        // z 600 dostępnych pytań do bazy po pełne treści idzie tylko 150 numerów, a kolejność egzaminu to kolejność losowania
        assertThat(captor.getValue()).hasSize(150);
        assertThat(response.questions()).extracting(TestQueueItemResponse::questionId)
                .containsExactlyElementsOf(new ArrayList<>(captor.getValue()));
    }

    @Test
    void start_ignoresTheUsersNarrowingToChosenActs_likeARealExam() {
        // użytkownik zawęził naukę do aktu 1, ale egzamin losuje z całej puli ścieżki (akty 1 i 2)
        pool = Pools.narrowedTo(Pools.all(), 1);
        List<Question> all = new ArrayList<>(questions(1, 5));
        all.add(QuestionRefs.question(6, 2, 1, QuestionRefs.OWN_SHEET));
        all.add(QuestionRefs.question(7, 2, 1, QuestionRefs.OWN_SHEET));
        givenPool(all, List.of());

        ExamStartResponse response = service.start(authentication);

        assertThat(response.questions()).extracting(TestQueueItemResponse::questionId)
                .containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L, 7L);
    }

    @Test
    void start_neverDrawsQuestionsSuspendedInTests() {
        givenPool(questions(1, 6), List.of(2L, 4L));

        // w puli jest mniej niż 150 pytań, więc egzamin ma wszystkie możliwe
        ExamStartResponse response = service.start(authentication);

        assertThat(response.questions()).extracting(TestQueueItemResponse::questionId)
                .containsExactlyInAnyOrder(1L, 3L, 5L, 6L);
        assertThat(response.requestedCount()).isEqualTo(150);
    }

    @Test
    void start_neverDrawsHiddenQuestionsOrActsOutsideThePool() {
        Question hidden = question(2);
        hidden.setHidden(true);
        Question foreignAct = QuestionRefs.question(3, 99, 1, QuestionRefs.OWN_SHEET);
        givenPool(List.of(question(1), hidden, foreignAct), List.of());

        ExamStartResponse response = service.start(authentication);

        assertThat(response.questions()).extracting(TestQueueItemResponse::questionId).containsExactly(1L);
    }

    @Test
    void start_drawsOnlyFromTheSelectedSourceGroups() {
        pool = Pools.of(new SourceFilter(true, false, true), 1, 2);
        List<Question> all = List.of(
                inSource(1, QuestionRefs.AUTHORED),
                inSource(2, QuestionRefs.OWN_SHEET),
                inSource(3, QuestionRefs.OTHER_SHEET),
                inSource(4, QuestionRefs.OWN_SHEET),
                inSource(5, QuestionRefs.OTHER_SHEET));
        givenPool(all, List.of());

        ExamStartResponse response = service.start(authentication);

        assertThat(response.questions()).extracting(TestQueueItemResponse::questionId)
                .containsExactlyInAnyOrder(1L, 2L, 4L);
    }

    @Test
    void start_whenTheFilterExcludesEverythingTheErrorPointsAtTheFilter() {
        pool = Pools.of(new SourceFilter(false, true, false), 1, 2);
        givenPool(List.of(inSource(1, QuestionRefs.AUTHORED), inSource(2, QuestionRefs.OWN_SHEET)), List.of());

        assertThatThrownBy(() -> service.start(authentication))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("filtr źródeł");
    }

    @Test
    void start_withNoQuestionsAtAllKeepsTheOriginalMessage() {
        pool = Pools.of(new SourceFilter(false, true, false), 1, 2);
        givenPool(List.of(), List.of());

        assertThatThrownBy(() -> service.start(authentication))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nie ma teraz pytań")
                .hasMessageNotContaining("filtr");
    }

    @Test
    void submit_gradesEveryShownQuestionEvenIfItsSourceIsNowFilteredOut() {
        pool = Pools.of(new SourceFilter(false, true, false), 1, 2);
        gradable(inSource(1, QuestionRefs.AUTHORED));

        ExamResultResponse result = service.submit(submitRequest(answer(1, CorrectOption.A)), authentication);

        assertThat(result.questions()).hasSize(1);
        assertThat(result.questions().get(0).correct()).isTrue();
    }

    @Test
    void start_withFewerThan150QuestionsReturnsAllOfThem_andScalesTheThreshold() {
        givenPool(questions(1, 12), List.of());

        ExamStartResponse response = service.start(authentication);

        assertThat(response.questions()).hasSize(12);
        assertThat(response.requestedCount()).isEqualTo(150);
        assertThat(response.passPoints()).isEqualTo(8); // 100 z 150, proporcjonalnie, w górę
    }

    @Test
    void start_withNothingToDrawIsABadRequest() {
        givenPool(questions(1, 3), List.of(1L, 2L, 3L));

        assertThatThrownBy(() -> service.start(authentication))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void start_writesNothingToTheDatabase() {
        givenPool(questions(1, 10), List.of());

        service.start(authentication);

        verify(attemptRepository, never()).save(any());
        verify(stateRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- ocena

    @Test
    void submit_scoresOnTheServer_andTreatsMissingAnswersAsWrong() {
        gradable(question(1, CorrectOption.A, "Egzamin 2024"), question(2, CorrectOption.B, "Egzamin 2024"),
                question(3, CorrectOption.C, "Egzamin 2024"));

        ExamResultResponse result = service.submit(
                submitRequest(answer(1, CorrectOption.A), answer(2, CorrectOption.C), answer(3, null)), authentication);

        ExamAttemptResponse attempt = result.attempt();
        assertThat(attempt.questionCount()).isEqualTo(3);
        assertThat(attempt.answeredCount()).isEqualTo(2);
        assertThat(attempt.correctCount()).isEqualTo(1);
        assertThat(attempt.scorePercent()).isEqualTo(33.3);
        assertThat(attempt.passed()).isFalse();
        assertThat(result.excludedCount()).isZero();

        assertThat(result.questions()).extracting(ExamResultResponse.QuestionResult::correct)
                .containsExactly(true, false, false);
        assertThat(result.questions().get(1).selectedOption()).isEqualTo(CorrectOption.C);
        assertThat(result.questions().get(1).correctOption()).isEqualTo(CorrectOption.B);
        assertThat(result.questions().get(2).selectedOption()).isNull();
        assertThat(result.questions().get(2).correctOption()).isEqualTo(CorrectOption.C);
    }

    @Test
    void submit_decidesPassingAgainstTheServerThreshold() {
        gradable(question(1), question(2), question(3), question(4));

        // 4 pytania: próg to 100/150 z 4, w górę, czyli 3 punkty
        ExamResultResponse atThreshold = service.submit(submitRequest(
                answer(1, CorrectOption.A), answer(2, CorrectOption.A), answer(3, CorrectOption.A), answer(4, CorrectOption.B)),
                authentication);
        ExamResultResponse below = service.submit(submitRequest(
                answer(1, CorrectOption.A), answer(2, CorrectOption.A), answer(3, CorrectOption.B), answer(4, CorrectOption.B)),
                authentication);

        assertThat(atThreshold.attempt().passed()).isTrue();
        assertThat(atThreshold.attempt().passPoints()).isEqualTo(3);
        assertThat(below.attempt().passed()).isFalse();
    }

    @Test
    void submit_fullExam_passesFromExactly100Points() {
        gradable(questions(1, 150).toArray(new Question[0]));

        ExamResultResponse with100 = service.submit(submitRequest(answers(150, 100)), authentication);
        ExamResultResponse with99 = service.submit(submitRequest(answers(150, 99)), authentication);

        assertThat(with100.attempt().questionCount()).isEqualTo(150);
        assertThat(with100.attempt().correctCount()).isEqualTo(100);
        assertThat(with100.attempt().passPoints()).isEqualTo(100);
        assertThat(with100.attempt().passed()).isTrue();
        assertThat(with99.attempt().correctCount()).isEqualTo(99);
        assertThat(with99.attempt().passed()).isFalse();
    }

    @Test
    void submit_savesThePassThresholdInPointsAndAsARoundedPercentage() {
        gradable(questions(1, 150).toArray(new Question[0]));

        service.submit(submitRequest(answers(150, 120)), authentication);

        ExamAttempt saved = savedAttempt();
        assertThat(saved.getPassPoints()).isEqualTo(100);
        assertThat(saved.getPassThresholdPercent()).isEqualTo(67); // 100/150 = 66,7%, w górę do pełnego procentu
        assertThat(saved.isPassed()).isTrue();
    }

    @Test
    void submit_savesTheSummaryOfTheAttempt() {
        gradable(question(1), question(2));

        ExamSubmitRequest request = new ExamSubmitRequest(90, 4_321, true,
                List.of(answer(1, CorrectOption.A), answer(2, null)));
        service.submit(request, authentication);

        ExamAttempt saved = savedAttempt();
        assertThat(saved.getUser().getId()).isEqualTo(5L);
        assertThat(saved.getFinishedAt()).isEqualTo(NOW);
        assertThat(saved.getScopeLabel()).isEqualTo("Cała pula");
        assertThat(saved.getQuestionCount()).isEqualTo(2);
        assertThat(saved.getAnsweredCount()).isEqualTo(1);
        assertThat(saved.getCorrectCount()).isEqualTo(1);
        assertThat(saved.getPassPoints()).isEqualTo(2); // 100/150 z 2 pytań, w górę
        assertThat(saved.getPassThresholdPercent()).isEqualTo(100);
        assertThat(saved.isPassed()).isFalse(); // 1 punkt z wymaganych 2
        assertThat(saved.getTimeLimitMinutes()).isEqualTo(90);
        assertThat(saved.getDurationSeconds()).isEqualTo(4_321);
        assertThat(saved.isTimedOut()).isTrue();
    }

    @Test
    void submit_withoutTimeLimitStoresNull() {
        gradable(question(1));

        service.submit(new ExamSubmitRequest(null, 10, null, List.of(answer(1, CorrectOption.A))), authentication);

        ExamAttempt saved = savedAttempt();
        assertThat(saved.getTimeLimitMinutes()).isNull();
        assertThat(saved.isTimedOut()).isFalse();
    }

    @Test
    void submit_keepsTheExamOrder_andBreaksDownBySource() {
        gradable(
                question(1, CorrectOption.A, "Zbiór autorski"),
                question(2, CorrectOption.A, "Egzamin 2024"),
                question(3, CorrectOption.A, "Egzamin 2024"),
                question(4, CorrectOption.A, "Egzamin 2024"));

        ExamResultResponse result = service.submit(submitRequest(
                answer(3, CorrectOption.A), answer(1, CorrectOption.B), answer(4, CorrectOption.A), answer(2, CorrectOption.C)),
                authentication);

        assertThat(result.questions()).extracting(ExamResultResponse.QuestionResult::questionId)
                .containsExactly(3L, 1L, 4L, 2L);
        // najpierw źródło z największą liczbą pytań
        assertThat(result.bySource()).extracting(ExamResultResponse.SourceBreakdown::sourceName)
                .containsExactly("Egzamin 2024", "Zbiór autorski");
        assertThat(result.bySource().get(0).total()).isEqualTo(3);
        assertThat(result.bySource().get(0).correct()).isEqualTo(2);
        assertThat(result.bySource().get(1).total()).isEqualTo(1);
        assertThat(result.bySource().get(1).correct()).isZero();
    }

    @Test
    void submit_resultNamesTheLegalActOfEveryQuestion() {
        gradable(question(1));

        ExamResultResponse result = service.submit(submitRequest(answer(1, CorrectOption.A)), authentication);

        assertThat(result.questions().get(0).actName()).isEqualTo("Akt 1");
        assertThat(result.questions().get(0).actShortName()).isEqualTo("A1");
    }

    @Test
    void submit_dropsQuestionsThatCannotBeScored_andSaysSo() {
        gradable(question(1), question(3)); // pytanie 2 zostało usunięte albo jest już niedostępne

        ExamResultResponse result = service.submit(submitRequest(
                answer(1, CorrectOption.A), answer(2, CorrectOption.A), answer(3, CorrectOption.A)), authentication);

        assertThat(result.excludedCount()).isEqualTo(1);
        assertThat(result.attempt().questionCount()).isEqualTo(2);
        assertThat(savedAttempt().getQuestionCount()).isEqualTo(2);
        assertThat(result.questions()).extracting(ExamResultResponse.QuestionResult::questionId).containsExactly(1L, 3L);
    }

    @Test
    void submit_withNothingToScoreIsABadRequest_andSavesNothing() {
        gradable();

        assertThatThrownBy(() -> service.submit(submitRequest(answer(1, CorrectOption.A)), authentication))
                .isInstanceOf(IllegalArgumentException.class);
        verify(attemptRepository, never()).save(any());
    }

    @Test
    void submit_rejectsTheSameQuestionTwice_andSavesNothing() {
        assertThatThrownBy(() -> service.submit(
                submitRequest(answer(1, CorrectOption.A), answer(1, CorrectOption.B)), authentication))
                .isInstanceOf(IllegalArgumentException.class);
        verify(attemptRepository, never()).save(any());
        verify(questionRepository, never()).findForGrading(ArgumentMatchers.<Collection<Long>>any());
    }

    @Test
    void submit_doesNotTouchTheLearningState() {
        gradable(question(1));

        service.submit(submitRequest(answer(1, CorrectOption.B)), authentication);

        // egzamin nie zmienia terminów powtórek ani liczników błędów: do stanów nauki w ogóle nie zagląda
        verifyNoInteractions(stateRepository);
    }

    @Test
    void submit_resultCarriesTheExplanationAndLegalBasisOnlyWhenThereIsOne() {
        Question withExplanation = question(1);
        withExplanation.setExplanation("Bo tak stanowi art. 1.");
        withExplanation.setLegalBasis("art. 1 k.c.");
        Question without = question(2);
        gradable(withExplanation, without);

        ExamResultResponse result = service.submit(
                submitRequest(answer(1, CorrectOption.A), answer(2, CorrectOption.A)), authentication);

        assertThat(result.questions().get(0).explanation()).isEqualTo("Bo tak stanowi art. 1.");
        assertThat(result.questions().get(0).legalBasis()).isEqualTo("art. 1 k.c.");
        assertThat(result.questions().get(1).explanation()).isNull();
        assertThat(result.questions().get(1).legalBasis()).isNull();
    }

    // ---------------------------------------------------------------- historia

    @Test
    void history_mapsAttemptsNewestFirst_withTheScoreInPercent() {
        ExamAttempt newer = attempt(2L, NOW, 100, 150, 67, 100, true);
        ExamAttempt older = attempt(1L, NOW.minusDays(3), 99, 150, 67, 100, false);
        when(attemptRepository.findRecent(eq(5L), ArgumentMatchers.<Pageable>any())).thenReturn(List.of(newer, older));

        List<ExamAttemptResponse> history = service.history(authentication);

        assertThat(history).extracting(ExamAttemptResponse::id).containsExactly(2L, 1L);
        assertThat(history.get(0).scorePercent()).isEqualTo(66.7);
        assertThat(history.get(0).passPoints()).isEqualTo(100);
        assertThat(history.get(0).passed()).isTrue();
        assertThat(history.get(1).scorePercent()).isEqualTo(66.0);
        assertThat(history.get(1).passed()).isFalse();
    }

    @Test
    void history_isEmptyWhenThereAreNoAttempts() {
        when(attemptRepository.findRecent(eq(5L), ArgumentMatchers.<Pageable>any())).thenReturn(List.of());

        assertThat(service.history(authentication)).isEmpty();
    }

    private static ExamAttempt attempt(long id, LocalDateTime finishedAt, int correct, int total, int threshold,
                                       Integer passPoints, boolean passed) {
        ExamAttempt attempt = new ExamAttempt();
        attempt.setId(id);
        attempt.setFinishedAt(finishedAt);
        attempt.setScopeLabel("Cała pula");
        attempt.setQuestionCount(total);
        attempt.setAnsweredCount(total);
        attempt.setCorrectCount(correct);
        attempt.setPassThresholdPercent(threshold);
        attempt.setPassPoints(passPoints);
        attempt.setPassed(passed);
        attempt.setTimeLimitMinutes(150);
        attempt.setDurationSeconds(7_000);
        return attempt;
    }
}
