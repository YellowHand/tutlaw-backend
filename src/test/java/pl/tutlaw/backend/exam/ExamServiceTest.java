package pl.tutlaw.backend.exam;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import pl.tutlaw.backend.common.PageParams;
import pl.tutlaw.backend.common.PageResponse;
import pl.tutlaw.backend.common.exception.ResourceNotFoundException;
import pl.tutlaw.backend.exam.dto.ExamAttemptResponse;
import pl.tutlaw.backend.exam.dto.ExamResultResponse;
import pl.tutlaw.backend.exam.dto.ExamSheetsResponse;
import pl.tutlaw.backend.exam.dto.ExamStartResponse;
import pl.tutlaw.backend.exam.dto.ExamSubmitRequest;
import pl.tutlaw.backend.pool.Pool;
import pl.tutlaw.backend.exam.dto.ExamAvailabilityResponse;
import pl.tutlaw.backend.pool.SourceFilter;
import pl.tutlaw.backend.pool.SourceMode;
import pl.tutlaw.backend.question.CorrectOption;
import pl.tutlaw.backend.question.Question;
import pl.tutlaw.backend.question.QuestionRepository;
import pl.tutlaw.backend.review.ReviewMode;
import pl.tutlaw.backend.review.UserQuestionStateRepository;
import pl.tutlaw.backend.review.dto.TestQueueItemResponse;
import pl.tutlaw.backend.settings.UserSettings;
import pl.tutlaw.backend.settings.UserSettingsRepository;
import pl.tutlaw.backend.source.QuestionSource;
import pl.tutlaw.backend.source.QuestionSourceRepository;
import pl.tutlaw.backend.source.SourceKind;
import pl.tutlaw.backend.track.Track;
import pl.tutlaw.backend.track.TrackKind;
import pl.tutlaw.backend.track.TrackRepository;
import pl.tutlaw.backend.testsupport.MutableClock;
import pl.tutlaw.backend.testsupport.Pools;
import pl.tutlaw.backend.testsupport.QuestionRefs;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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
    @Mock private ExamAttemptAnswerRepository answerRepository;
    @Mock private CurrentUserResolver currentUserResolver;
    @Mock private QuestionSourceRepository sourceRepository;
    @Mock private TrackRepository trackRepository;
    @Mock private UserSettingsRepository settingsRepository;
    private final Authentication authentication = mock(Authentication.class);

    private Pool pool = Pools.all();
    private ExamService service;

    @BeforeEach
    void setUp() {
        service = new ExamService(questionRepository, stateRepository, attemptRepository, answerRepository, currentUserResolver, clock,
                Pools.holding(() -> pool), sourceRepository, trackRepository, settingsRepository);
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
        return new ExamSubmitRequest(150, 3_600, false, List.of(answers), null, null);
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

        ExamStartResponse response = service.start(null, null, authentication);

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

        ExamStartResponse response = service.start(null, null, authentication);

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

        ExamStartResponse response = service.start(null, null, authentication);

        assertThat(response.questions()).extracting(TestQueueItemResponse::questionId)
                .containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L, 7L);
    }

    @Test
    void start_neverDrawsQuestionsSuspendedInTests() {
        givenPool(questions(1, 6), List.of(2L, 4L));

        // w puli jest mniej niż 150 pytań, więc egzamin ma wszystkie możliwe
        ExamStartResponse response = service.start(null, null, authentication);

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

        ExamStartResponse response = service.start(null, null, authentication);

        assertThat(response.questions()).extracting(TestQueueItemResponse::questionId).containsExactly(1L);
    }

    @Test
    void start_drawsFromEverySource() {
        List<Question> all = List.of(
                inSource(1, QuestionRefs.AUTHORED),
                inSource(2, QuestionRefs.OWN_SHEET),
                inSource(3, QuestionRefs.OTHER_SHEET),
                inSource(4, QuestionRefs.OWN_SHEET),
                inSource(5, QuestionRefs.OTHER_SHEET));
        givenPool(all, List.of());

        ExamStartResponse response = service.start(null, null, authentication);

        assertThat(response.questions()).extracting(TestQueueItemResponse::questionId)
                .containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L);
    }

    // ---------------------------------------------------------------- wybór źródła

    private List<Question> mixedSources() {
        return List.of(
                inSource(1, QuestionRefs.AUTHORED),
                inSource(2, QuestionRefs.OWN_SHEET),
                inSource(3, QuestionRefs.OTHER_SHEET),
                inSource(4, QuestionRefs.OWN_SHEET),
                inSource(5, QuestionRefs.OTHER_SHEET),
                inSource(6, QuestionRefs.AUTHORED));
    }

    @Test
    void start_withMinistrySource_drawsOnlyFromTheExamSheets() {
        givenPool(mixedSources(), List.of());

        ExamStartResponse response = service.start(SourceMode.MINISTRY, null, authentication);

        assertThat(response.questions()).extracting(TestQueueItemResponse::questionId)
                .containsExactlyInAnyOrder(2L, 3L, 4L, 5L);
        assertThat(response.scopeLabel()).isEqualTo("Ministerstwo Sprawiedliwości");
    }

    @Test
    void start_withAuthoredSource_drawsOnlyAuthoredQuestions() {
        givenPool(mixedSources(), List.of());

        ExamStartResponse response = service.start(SourceMode.AUTHORED, null, authentication);

        assertThat(response.questions()).extracting(TestQueueItemResponse::questionId)
                .containsExactlyInAnyOrder(1L, 6L);
        assertThat(response.scopeLabel()).isEqualTo("Pytania autorskie");
    }

    @Test
    void start_withAllSources_orWithoutChoice_drawsFromEverything() {
        givenPool(mixedSources(), List.of());

        assertThat(service.start(SourceMode.ALL, null, authentication).questions()).hasSize(6);
        assertThat(service.start(null, null, authentication).questions()).hasSize(6);
        assertThat(service.start(null, null, authentication).scopeLabel()).isEqualTo("Cała pula");
    }

    @Test
    void start_withASourceThatHasNoQuestions_saysSoAndNamesTheRemedy() {
        givenPool(List.of(inSource(1, QuestionRefs.OWN_SHEET), inSource(2, QuestionRefs.OTHER_SHEET)), List.of());

        assertThatThrownBy(() -> service.start(SourceMode.AUTHORED, null, authentication))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("z tego źródła")
                .hasMessageContaining("Wybierz inne źródło");
    }

    @Test
    void start_withASource_stillIgnoresTheNarrowingToChosenActs() {
        pool = Pools.narrowedTo(Pools.all(), 1);
        List<Question> all = List.of(
                QuestionRefs.question(1, 1, 1, QuestionRefs.OWN_SHEET),
                QuestionRefs.question(2, 2, 1, QuestionRefs.OWN_SHEET));
        givenPool(all, List.of());

        ExamStartResponse response = service.start(SourceMode.MINISTRY, null, authentication);

        assertThat(response.questions()).extracting(TestQueueItemResponse::questionId).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void start_withAShortSource_returnsAllOfItAndScalesTheThreshold() {
        List<Question> all = new ArrayList<>(questions(1, 100));
        all.add(inSource(101, QuestionRefs.AUTHORED));
        all.add(inSource(102, QuestionRefs.AUTHORED));
        all.add(inSource(103, QuestionRefs.AUTHORED));
        all.add(inSource(104, QuestionRefs.AUTHORED));
        all.add(inSource(105, QuestionRefs.AUTHORED));
        all.add(inSource(106, QuestionRefs.AUTHORED));
        givenPool(all, List.of());

        ExamStartResponse response = service.start(SourceMode.AUTHORED, null, authentication);

        assertThat(response.questions()).hasSize(6);
        assertThat(response.requestedCount()).isEqualTo(150);
        assertThat(response.passPoints()).isEqualTo(4); // 6 * 100 / 150 = 4 dokładnie
    }

    @Test
    void availability_countsEachSourceSeparately_withoutSuspendedQuestions() {
        givenPool(mixedSources(), List.of(2L, 6L));

        ExamAvailabilityResponse availability = service.availability(authentication);

        assertThat(availability.all()).isEqualTo(4);
        assertThat(availability.ministry()).isEqualTo(3);
        assertThat(availability.authored()).isEqualTo(1);
    }

    @Test
    void availability_ignoresTheNarrowingToChosenActs() {
        pool = Pools.narrowedTo(Pools.all(), 1);
        givenPool(List.of(
                QuestionRefs.question(1, 1, 1, QuestionRefs.OWN_SHEET),
                QuestionRefs.question(2, 2, 1, QuestionRefs.AUTHORED)), List.of());

        ExamAvailabilityResponse availability = service.availability(authentication);

        assertThat(availability.all()).isEqualTo(2);
        assertThat(availability.ministry()).isEqualTo(1);
        assertThat(availability.authored()).isEqualTo(1);
    }

    @Test
    void submit_labelsTheAttemptWithTheChosenSource() {
        gradable(question(1));

        service.submit(new ExamSubmitRequest(null, 10, null, List.of(answer(1, CorrectOption.A)), SourceMode.MINISTRY, null), authentication);

        assertThat(savedAttempt().getScopeLabel()).isEqualTo("Ministerstwo Sprawiedliwości");
    }

    @Test
    void start_withNoQuestionsAtAllSaysThereAreNone() {
        givenPool(List.of(), List.of());

        assertThatThrownBy(() -> service.start(null, null, authentication))
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

        ExamStartResponse response = service.start(null, null, authentication);

        assertThat(response.questions()).hasSize(12);
        assertThat(response.requestedCount()).isEqualTo(150);
        assertThat(response.passPoints()).isEqualTo(8); // 100 z 150, proporcjonalnie, w górę
    }

    @Test
    void start_withNothingToDrawIsABadRequest() {
        givenPool(questions(1, 3), List.of(1L, 2L, 3L));

        assertThatThrownBy(() -> service.start(null, null, authentication))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void start_writesNothingToTheDatabase() {
        givenPool(questions(1, 10), List.of());

        service.start(null, null, authentication);

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
                List.of(answer(1, CorrectOption.A), answer(2, null)), null, null);
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

        service.submit(new ExamSubmitRequest(null, 10, null, List.of(answer(1, CorrectOption.A)), null, null), authentication);

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

    // ---------------------------------------------------------------- egzaminy MS (arkusze z poprzednich lat)

    private static QuestionSource sheet(long id, String name, int year) {
        QuestionSource source = new QuestionSource();
        source.setId(id);
        source.setSlug("arkusz-" + id);
        source.setKind(SourceKind.EXAM_SHEET);
        source.setName(name);
        source.setExamYear(year);
        return source;
    }

    private static QuestionSourceRepository.SourceRow row(long id, SourceKind kind, Long trackId, Integer year) {
        return new QuestionSourceRepository.SourceRow() {
            @Override public Long getId() { return id; }
            @Override public String getSlug() { return "zrodlo-" + id; }
            @Override public SourceKind getKind() { return kind; }
            @Override public String getName() { return "Źródło " + id; }
            @Override public Long getTrackId() { return trackId; }
            @Override public Integer getExamYear() { return year; }
        };
    }

    private static Track track(long id, String shortName, int sortOrder, TrackKind kind) {
        Track track = new Track();
        track.setId(id);
        track.setSlug("aplikacja-" + id);
        track.setName("Aplikacja " + shortName);
        track.setShortName(shortName);
        track.setKind(kind);
        track.setSortOrder(sortOrder);
        return track;
    }

    /** Pytania numerów {@code from..to} w arkuszu {@code sheetId} (akt 1, dziedzina 1). */
    private static List<Question> inSheet(long sheetId, long from, long to) {
        List<Question> list = new ArrayList<>();
        for (long id = from; id <= to; id++) {
            list.add(QuestionRefs.question(id, 1, 1, sheetId));
        }
        return list;
    }

    /** Arkusz o danym numerze i jego pytania; lenient, bo nie każdy test dociera do wczytywania pełnych pytań. */
    private void givenSheet(QuestionSource source, List<Question> all, List<Long> suspendedInTests) {
        when(sourceRepository.findById(source.getId())).thenReturn(java.util.Optional.of(source));
        lenient().when(questionRepository.findRefsInSources(ArgumentMatchers.<Collection<Long>>any()))
                .thenAnswer(inv -> QuestionRefs.inSources(all, inv.getArgument(0)));
        lenient().when(questionRepository.findAllWithRelationsByIdIn(any()))
                .thenAnswer(inv -> QuestionRefs.byIds(all, inv.getArgument(0)));
        lenient().when(stateRepository.findSuspendedQuestionIds(5L, ReviewMode.TEST)).thenReturn(suspendedInTests);
    }

    @Test
    void startSheet_returnsTheWholeSheetInItsOwnOrder_withoutShuffling() {
        List<Question> all = new ArrayList<>(inSheet(7, 1, 120));
        all.addAll(inSheet(8, 200, 260)); // inny arkusz: nie może się pojawić
        givenSheet(sheet(7, "Notarialna 2024", 2024), all, List.of());

        ExamStartResponse response = service.start(null, 7L, authentication);

        assertThat(response.questions()).extracting(TestQueueItemResponse::questionId)
                .containsExactlyElementsOf(java.util.stream.LongStream.rangeClosed(1, 120).boxed().toList());
        assertThat(response.scopeLabel()).isEqualTo("Egzamin MS: Notarialna 2024");
    }

    @Test
    void startSheet_fullSheetHas150Questions_andPassesFrom100Points() {
        givenSheet(sheet(7, "Komornicza 2019", 2019), inSheet(7, 1, 150), List.of());

        ExamStartResponse response = service.start(null, 7L, authentication);

        assertThat(response.questions()).hasSize(150);
        assertThat(response.requestedCount()).isEqualTo(150);
        assertThat(response.passPoints()).isEqualTo(100);
    }

    @Test
    void startSheet_shorterSheetScalesTheThreshold() {
        givenSheet(sheet(7, "Komornicza 2019", 2019), inSheet(7, 1, 120), List.of());

        ExamStartResponse response = service.start(null, 7L, authentication);

        assertThat(response.questions()).hasSize(120);
        assertThat(response.passPoints()).isEqualTo(80); // 120 * 100 / 150 = 80 dokładnie
    }

    @Test
    void startSheet_isCutAt150_becauseThatIsTheExamFormat() {
        givenSheet(sheet(7, "Notarialna 2024", 2024), inSheet(7, 1, 160), List.of());

        ExamStartResponse response = service.start(null, 7L, authentication);

        assertThat(response.questions()).hasSize(150);
        assertThat(response.questions().get(0).questionId()).isEqualTo(1L);
        assertThat(response.questions().get(149).questionId()).isEqualTo(150L);
    }

    @Test
    void startSheet_ignoresThePoolTheTrackAndTheNarrowing() {
        pool = Pools.narrowedTo(Pools.all(), 1);
        List<Question> all = List.of(
                QuestionRefs.question(1, 1, 1, 7),
                QuestionRefs.question(2, 99, 5, 7)); // akt spoza puli i spoza zawężenia
        givenSheet(sheet(7, "Notarialna 2024", 2024), all, List.of());

        ExamStartResponse response = service.start(null, 7L, authentication);

        assertThat(response.questions()).extracting(TestQueueItemResponse::questionId).containsExactly(1L, 2L);
    }

    @Test
    void startSheet_skipsQuestionsSuspendedInTests() {
        givenSheet(sheet(7, "Notarialna 2024", 2024), inSheet(7, 1, 10), List.of(3L, 4L));

        ExamStartResponse response = service.start(null, 7L, authentication);

        assertThat(response.questions()).extracting(TestQueueItemResponse::questionId)
                .containsExactly(1L, 2L, 5L, 6L, 7L, 8L, 9L, 10L);
    }

    @Test
    void startSheet_skipsHiddenQuestions() {
        List<Question> all = new ArrayList<>(inSheet(7, 1, 5));
        all.get(1).setHidden(true);
        givenSheet(sheet(7, "Notarialna 2024", 2024), all, List.of());

        assertThat(service.start(null, 7L, authentication).questions())
                .extracting(TestQueueItemResponse::questionId).containsExactly(1L, 3L, 4L, 5L);
    }

    @Test
    void startSheet_takesPrecedenceOverTheSourceChoice() {
        List<Question> all = new ArrayList<>(inSheet(7, 1, 5));
        all.add(inSource(50, QuestionRefs.AUTHORED));
        givenSheet(sheet(7, "Notarialna 2024", 2024), all, List.of());

        ExamStartResponse response = service.start(SourceMode.AUTHORED, 7L, authentication);

        assertThat(response.questions()).extracting(TestQueueItemResponse::questionId).containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(response.scopeLabel()).isEqualTo("Egzamin MS: Notarialna 2024");
    }

    @Test
    void startSheet_withAnUnknownSheet_isABadRequest() {
        when(sourceRepository.findById(404L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.start(null, 404L, authentication))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nie ma takiego egzaminu MS");
        verify(questionRepository, never()).findRefsInSources(any());
    }

    @Test
    void startSheet_withAnAuthoredSource_isABadRequest_becauseItIsNotAnExam() {
        QuestionSource authored = QuestionRefs.source(QuestionRefs.AUTHORED);
        when(sourceRepository.findById(QuestionRefs.AUTHORED)).thenReturn(java.util.Optional.of(authored));

        assertThatThrownBy(() -> service.start(null, QuestionRefs.AUTHORED, authentication))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nie ma takiego egzaminu MS");
    }

    @Test
    void startSheet_withNothingLeftToSolve_isABadRequest_andNamesTheRemedy() {
        givenSheet(sheet(7, "Notarialna 2024", 2024), inSheet(7, 1, 3), List.of(1L, 2L, 3L));

        assertThatThrownBy(() -> service.start(null, 7L, authentication))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Z tego egzaminu MS nie ma teraz pytań")
                .hasMessageContaining("Wybierz inny egzamin");
    }

    @Test
    void startSheet_writesNothingToTheDatabase() {
        givenSheet(sheet(7, "Notarialna 2024", 2024), inSheet(7, 1, 5), List.of());

        service.start(null, 7L, authentication);

        verify(attemptRepository, never()).save(any());
        verify(stateRepository, never()).save(any());
    }

    // ---- lista egzaminów MS

    /** Aplikacje: 1 adwokacka (10), 2 sędziowska (20), 3 notarialna (40), 9 Student. */
    private void givenTracks(Long usersTrackId) {
        when(trackRepository.findAllByOrderBySortOrderAscIdAsc()).thenReturn(List.of(
                track(1, "Adwokacka i radcowska", 10, TrackKind.EXAM),
                track(2, "Sędziowska i prokuratorska", 20, TrackKind.EXAM),
                track(3, "Notarialna", 40, TrackKind.EXAM),
                track(9, "Student", 60, TrackKind.STUDENT)));
        UserSettings settings = new UserSettings(5L);
        settings.setTrackId(usersTrackId);
        lenient().when(settingsRepository.findById(5L)).thenReturn(java.util.Optional.of(settings));
    }

    private void givenSheetRows(QuestionSourceRepository.SourceRow... rows) {
        when(sourceRepository.findAllRows()).thenReturn(List.of(rows));
    }

    private void givenSheetQuestions(List<Question> all, List<Long> suspended) {
        when(questionRepository.findRefsInSources(ArgumentMatchers.<Collection<Long>>any()))
                .thenAnswer(inv -> QuestionRefs.inSources(all, inv.getArgument(0)));
        when(stateRepository.findSuspendedQuestionIds(5L, ReviewMode.TEST)).thenReturn(suspended);
    }

    @Test
    void sheets_groupsByApplication_newestYearFirst() {
        givenTracks(null);
        givenSheetRows(
                row(101, SourceKind.EXAM_SHEET, 1L, 2022),
                row(102, SourceKind.EXAM_SHEET, 1L, 2024),
                row(103, SourceKind.EXAM_SHEET, 1L, 2023),
                row(104, SourceKind.EXAM_SHEET, 2L, 2021));
        List<Question> all = new ArrayList<>();
        all.addAll(inSheet(101, 1, 3));
        all.addAll(inSheet(102, 11, 15));
        all.addAll(inSheet(103, 21, 22));
        all.addAll(inSheet(104, 31, 31));
        givenSheetQuestions(all, List.of());

        ExamSheetsResponse response = service.sheets(authentication);

        assertThat(response.applications()).extracting(ExamSheetsResponse.Application::shortName)
                .containsExactly("Adwokacka i radcowska", "Sędziowska i prokuratorska");
        ExamSheetsResponse.Application first = response.applications().get(0);
        assertThat(first.sheets()).extracting(ExamSheetsResponse.Sheet::year).containsExactly(2024, 2023, 2022);
        assertThat(first.sheets()).extracting(ExamSheetsResponse.Sheet::id).containsExactly(102L, 103L, 101L);
        assertThat(first.sheets()).extracting(ExamSheetsResponse.Sheet::questionCount).containsExactly(5, 2, 3);
        assertThat(response.applications().get(1).sheets()).hasSize(1);
    }

    @Test
    void sheets_putsTheUsersOwnApplicationFirst_andMarksIt() {
        givenTracks(3L);
        givenSheetRows(
                row(101, SourceKind.EXAM_SHEET, 1L, 2024),
                row(103, SourceKind.EXAM_SHEET, 3L, 2020));
        List<Question> all = new ArrayList<>();
        all.addAll(inSheet(101, 1, 2));
        all.addAll(inSheet(103, 11, 12));
        givenSheetQuestions(all, List.of());

        ExamSheetsResponse response = service.sheets(authentication);

        assertThat(response.applications()).extracting(ExamSheetsResponse.Application::shortName)
                .containsExactly("Notarialna", "Adwokacka i radcowska");
        assertThat(response.applications()).extracting(ExamSheetsResponse.Application::own).containsExactly(true, false);
    }

    @Test
    void sheets_withoutAChosenTrack_marksNoApplicationAsOwn() {
        when(trackRepository.findAllByOrderBySortOrderAscIdAsc())
                .thenReturn(List.of(track(1, "Adwokacka i radcowska", 10, TrackKind.EXAM)));
        when(settingsRepository.findById(5L)).thenReturn(java.util.Optional.empty());
        givenSheetRows(row(101, SourceKind.EXAM_SHEET, 1L, 2024));
        givenSheetQuestions(inSheet(101, 1, 2), List.of());

        ExamSheetsResponse response = service.sheets(authentication);

        assertThat(response.applications()).extracting(ExamSheetsResponse.Application::own).containsExactly(false);
    }

    @Test
    void sheets_countsOnlyWhatCanBeSolved_andCapsAtTheExamFormat() {
        givenTracks(null);
        givenSheetRows(
                row(101, SourceKind.EXAM_SHEET, 1L, 2024),
                row(102, SourceKind.EXAM_SHEET, 1L, 2023));
        List<Question> all = new ArrayList<>();
        all.addAll(inSheet(101, 1, 10));       // 10, z czego 2 zawieszone: 8
        all.addAll(inSheet(102, 100, 259));    // 160 pytań: najwyżej 150
        all.get(9).setHidden(true);            // pytanie 10 ukryte: 7
        givenSheetQuestions(all, List.of(1L, 2L));

        ExamSheetsResponse response = service.sheets(authentication);

        assertThat(response.applications().get(0).sheets()).extracting(ExamSheetsResponse.Sheet::questionCount)
                .containsExactly(7, 150); // od najnowszego roku: 2024 (7), potem 2023 (150, obcięte z 160)
    }

    @Test
    void sheets_omitsSheetsWithNothingToSolve_andApplicationsWithoutSheets() {
        givenTracks(null);
        givenSheetRows(
                row(101, SourceKind.EXAM_SHEET, 1L, 2024),  // wszystkie pytania zawieszone
                row(102, SourceKind.EXAM_SHEET, 2L, 2024),  // jedyny arkusz aplikacji 2 jest pusty
                row(103, SourceKind.EXAM_SHEET, 3L, 2019),  // ten zostaje
                row(104, SourceKind.EXAM_SHEET, 3L, 2020)); // pusty arkusz bez żadnego pytania
        List<Question> all = new ArrayList<>();
        all.addAll(inSheet(101, 1, 2));
        all.addAll(inSheet(103, 11, 13));
        givenSheetQuestions(all, List.of(1L, 2L));

        ExamSheetsResponse response = service.sheets(authentication);

        assertThat(response.applications()).extracting(ExamSheetsResponse.Application::shortName).containsExactly("Notarialna");
        assertThat(response.applications().get(0).sheets()).extracting(ExamSheetsResponse.Sheet::year).containsExactly(2019);
    }

    @Test
    void sheets_ignoresAuthoredSourcesAndTheStudentTrack() {
        givenTracks(null);
        givenSheetRows(
                row(3, SourceKind.AUTHORED, null, null),
                row(109, SourceKind.EXAM_SHEET, 9L, 2024), // arkusz przypisany do Studenta: nie jest aplikacją
                row(101, SourceKind.EXAM_SHEET, 1L, 2024));
        List<Question> all = new ArrayList<>();
        all.add(inSource(1, QuestionRefs.AUTHORED));
        all.addAll(inSheet(109, 5, 6));
        all.addAll(inSheet(101, 11, 12));
        givenSheetQuestions(all, List.of());

        ExamSheetsResponse response = service.sheets(authentication);

        assertThat(response.applications()).extracting(ExamSheetsResponse.Application::trackId).containsExactly(1L);
    }

    @Test
    void sheets_isEmptyWhenThereAreNoSheetsAtAll() {
        givenSheetRows();

        assertThat(service.sheets(authentication).applications()).isEmpty();
        verify(questionRepository, never()).findRefsInSources(any());
    }

    @Test
    void sheets_writesNothing() {
        givenTracks(null);
        givenSheetRows(row(101, SourceKind.EXAM_SHEET, 1L, 2024));
        givenSheetQuestions(inSheet(101, 1, 2), List.of());

        service.sheets(authentication);

        verify(attemptRepository, never()).save(any());
        verify(stateRepository, never()).save(any());
    }

    // ---- podpis w historii

    @Test
    void submit_labelsTheAttemptWithTheChosenExamSheet() {
        gradable(question(1));
        when(sourceRepository.findById(7L)).thenReturn(java.util.Optional.of(sheet(7, "Notarialna 2024", 2024)));

        service.submit(new ExamSubmitRequest(null, 10, null, List.of(answer(1, CorrectOption.A)), SourceMode.MINISTRY, 7L), authentication);

        assertThat(savedAttempt().getScopeLabel()).isEqualTo("Egzamin MS: Notarialna 2024");
    }

    @Test
    void submit_withAVanishedSheet_stillSavesTheResult_underAGenericLabel() {
        gradable(question(1));
        when(sourceRepository.findById(7L)).thenReturn(java.util.Optional.empty());

        ExamResultResponse result = service.submit(
                new ExamSubmitRequest(null, 10, null, List.of(answer(1, CorrectOption.A)), null, 7L), authentication);

        assertThat(savedAttempt().getScopeLabel()).isEqualTo("Egzamin MS");
        assertThat(result.attempt().correctCount()).isEqualTo(1);
    }

    @Test
    void submit_withASheetIdThatIsNotAnExamSheet_usesTheGenericLabel() {
        gradable(question(1));
        when(sourceRepository.findById(QuestionRefs.AUTHORED))
                .thenReturn(java.util.Optional.of(QuestionRefs.source(QuestionRefs.AUTHORED)));

        service.submit(new ExamSubmitRequest(null, 10, null, List.of(answer(1, CorrectOption.A)), null, QuestionRefs.AUTHORED), authentication);

        assertThat(savedAttempt().getScopeLabel()).isEqualTo("Egzamin MS");
    }

    @Test
    void submit_withoutASheet_doesNotLookAnySheetUp() {
        gradable(question(1));

        service.submit(new ExamSubmitRequest(null, 10, null, List.of(answer(1, CorrectOption.A)), SourceMode.AUTHORED, null), authentication);

        verifyNoInteractions(sourceRepository);
        assertThat(savedAttempt().getScopeLabel()).isEqualTo("Pytania autorskie");
    }

    // ---------------------------------------------------------------- zapis odpowiedzi

    @SuppressWarnings("unchecked")
    private List<ExamAttemptAnswer> savedAnswers() {
        ArgumentCaptor<Iterable<ExamAttemptAnswer>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(answerRepository).saveAll(captor.capture());
        List<ExamAttemptAnswer> rows = new ArrayList<>();
        captor.getValue().forEach(rows::add);
        return rows;
    }

    @Test
    void submit_storesEveryAnswerInTheExamOrder_withNullForAQuestionLeftBlank() {
        gradable(question(1), question(2), question(3));

        service.submit(submitRequest(answer(3, CorrectOption.B), answer(1, null), answer(2, CorrectOption.A)), authentication);

        List<ExamAttemptAnswer> rows = savedAnswers();
        assertThat(rows).extracting(ExamAttemptAnswer::getPosition).containsExactly(1, 2, 3);
        assertThat(rows).extracting(r -> r.getQuestion().getId()).containsExactly(3L, 1L, 2L);
        assertThat(rows).extracting(ExamAttemptAnswer::getSelectedOption)
                .containsExactly(CorrectOption.B, null, CorrectOption.A);
        ExamAttempt saved = savedAttempt();
        assertThat(rows).allMatch(r -> r.getAttempt() == saved);
    }

    @Test
    void submit_marksTheAttemptAsHavingAnswers_andRemembersHowManyQuestionsWereExcluded() {
        gradable(question(1));

        ExamResultResponse result = service.submit(
                submitRequest(answer(1, CorrectOption.A), answer(99, CorrectOption.B)), authentication);

        ExamAttempt saved = savedAttempt();
        assertThat(saved.isHasAnswers()).isTrue();
        assertThat(saved.getExcludedCount()).isEqualTo(1);
        assertThat(result.excludedCount()).isEqualTo(1);
        assertThat(result.attempt().detailsAvailable()).isTrue();
        // pytanie, którego nie dało się ocenić, nie dostaje wiersza i nie zajmuje miejsca w kolejności
        assertThat(savedAnswers()).extracting(r -> r.getQuestion().getId()).containsExactly(1L);
        assertThat(savedAnswers()).extracting(ExamAttemptAnswer::getPosition).containsExactly(1);
    }

    @Test
    void submit_storesNothing_whenNoneOfTheQuestionsCanBeGraded() {
        gradable();

        assertThatThrownBy(() -> service.submit(submitRequest(answer(99, CorrectOption.A)), authentication))
                .isInstanceOf(IllegalArgumentException.class);

        verify(attemptRepository, never()).save(any());
        verify(answerRepository, never()).saveAll(ArgumentMatchers.<Iterable<ExamAttemptAnswer>>any());
    }

    // ---------------------------------------------------------------- raport podejścia

    private ExamAttempt storedAttempt(long id, boolean hasAnswers) {
        ExamAttempt attempt = attempt(id, NOW, 2, 2, 100, 2, true);
        attempt.setHasAnswers(hasAnswers);
        attempt.setExcludedCount(1);
        return attempt;
    }

    private static ExamAttemptAnswer storedAnswer(Question question, int position, CorrectOption selected) {
        ExamAttemptAnswer row = new ExamAttemptAnswer();
        row.setQuestion(question);
        row.setPosition(position);
        row.setSelectedOption(selected);
        return row;
    }

    @Test
    void attempt_rebuildsTheReportFromTheStoredAnswers_withTheCurrentQuestionContent() {
        Question first = question(1, CorrectOption.A, "Zbiór autorski");
        first.setBody("Treść po poprawce");
        Question second = question(2, CorrectOption.A, "Egzamin 2024");
        when(attemptRepository.findByIdAndUserId(7L, 5L)).thenReturn(Optional.of(storedAttempt(7L, true)));
        when(answerRepository.findDetailed(7L)).thenReturn(List.of(
                storedAnswer(first, 1, CorrectOption.A), storedAnswer(second, 2, CorrectOption.C)));

        ExamResultResponse result = service.attempt(7L, authentication);

        assertThat(result.attempt().id()).isEqualTo(7L);
        assertThat(result.attempt().detailsAvailable()).isTrue();
        assertThat(result.excludedCount()).isEqualTo(1);
        assertThat(result.questions()).extracting(ExamResultResponse.QuestionResult::questionId).containsExactly(1L, 2L);
        assertThat(result.questions().get(0).body()).isEqualTo("Treść po poprawce");
        assertThat(result.questions().get(0).selectedOption()).isEqualTo(CorrectOption.A);
        assertThat(result.questions().get(0).correct()).isTrue();
        assertThat(result.questions().get(1).selectedOption()).isEqualTo(CorrectOption.C);
        assertThat(result.questions().get(1).correct()).isFalse();
        assertThat(result.bySource()).extracting(ExamResultResponse.SourceBreakdown::sourceName)
                .containsExactlyInAnyOrder("Zbiór autorski", "Egzamin 2024");
    }

    @Test
    void attempt_keepsTheStoredScore_butJudgesEachQuestionAgainstTheCurrentKey() {
        // po egzaminie administrator poprawił klucz pytania z A na B: odpowiedź A nie jest już poprawna
        Question corrected = question(1, CorrectOption.B, "Egzamin 2024");
        ExamAttempt stored = storedAttempt(7L, true);
        stored.setCorrectCount(1);
        when(attemptRepository.findByIdAndUserId(7L, 5L)).thenReturn(Optional.of(stored));
        when(answerRepository.findDetailed(7L)).thenReturn(List.of(storedAnswer(corrected, 1, CorrectOption.A)));

        ExamResultResponse result = service.attempt(7L, authentication);

        assertThat(result.attempt().correctCount()).isEqualTo(1);
        assertThat(result.questions().get(0).correct()).isFalse();
        assertThat(result.questions().get(0).correctOption()).isEqualTo(CorrectOption.B);
        assertThat(result.bySource().get(0).correct()).isZero();
    }

    @Test
    void attempt_showsAQuestionLeftBlankAsNoAnswer() {
        when(attemptRepository.findByIdAndUserId(7L, 5L)).thenReturn(Optional.of(storedAttempt(7L, true)));
        when(answerRepository.findDetailed(7L)).thenReturn(List.of(storedAnswer(question(1), 1, null)));

        ExamResultResponse result = service.attempt(7L, authentication);

        assertThat(result.questions().get(0).selectedOption()).isNull();
        assertThat(result.questions().get(0).correct()).isFalse();
    }

    @Test
    void attempt_isNotFound_whenTheAttemptHasNoStoredAnswers() {
        when(attemptRepository.findByIdAndUserId(7L, 5L)).thenReturn(Optional.of(storedAttempt(7L, false)));

        assertThatThrownBy(() -> service.attempt(7L, authentication)).isInstanceOf(ResourceNotFoundException.class);
        verify(answerRepository, never()).findDetailed(any());
    }

    @Test
    void attempt_isNotFound_forSomeoneElsesOrMissingAttempt() {
        when(attemptRepository.findByIdAndUserId(7L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.attempt(7L, authentication)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------------------------------------------------------------- historia

    @Test
    void history_mapsAttemptsNewestFirst_withTheScoreInPercent() {
        ExamAttempt newer = attempt(2L, NOW, 100, 150, 67, 100, true);
        ExamAttempt older = attempt(1L, NOW.minusDays(3), 99, 150, 67, 100, false);
        when(attemptRepository.findPage(eq(5L), ArgumentMatchers.<Pageable>any()))
                .thenReturn(new PageImpl<>(List.of(newer, older), PageRequest.of(0, 20), 2));

        PageResponse<ExamAttemptResponse> history = service.history(PageParams.ofList(null, null), authentication);

        assertThat(history.items()).extracting(ExamAttemptResponse::id).containsExactly(2L, 1L);
        assertThat(history.items().get(0).scorePercent()).isEqualTo(66.7);
        assertThat(history.items().get(0).passPoints()).isEqualTo(100);
        assertThat(history.items().get(0).passed()).isTrue();
        assertThat(history.items().get(1).scorePercent()).isEqualTo(66.0);
        assertThat(history.items().get(1).passed()).isFalse();
    }

    @Test
    void history_asksForTheRequestedPage_andReportsTheTotals() {
        ExamAttempt only = attempt(2L, NOW, 100, 150, 67, 100, true);
        when(attemptRepository.findPage(eq(5L), ArgumentMatchers.<Pageable>any()))
                .thenReturn(new PageImpl<>(List.of(only), PageRequest.of(2, 10), 21));

        PageResponse<ExamAttemptResponse> history = service.history(PageParams.ofList(2, 10), authentication);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(attemptRepository).findPage(eq(5L), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);
        assertThat(history.page()).isEqualTo(2);
        assertThat(history.size()).isEqualTo(10);
        assertThat(history.totalElements()).isEqualTo(21);
        assertThat(history.totalPages()).isEqualTo(3);
    }

    @Test
    void history_flagsWhichAttemptsHaveAReport() {
        ExamAttempt withReport = attempt(2L, NOW, 100, 150, 67, 100, true);
        withReport.setHasAnswers(true);
        ExamAttempt old = attempt(1L, NOW.minusDays(30), 99, 150, 67, null, false);
        when(attemptRepository.findPage(eq(5L), ArgumentMatchers.<Pageable>any()))
                .thenReturn(new PageImpl<>(List.of(withReport, old), PageRequest.of(0, 20), 2));

        PageResponse<ExamAttemptResponse> history = service.history(PageParams.ofList(null, null), authentication);

        assertThat(history.items()).extracting(ExamAttemptResponse::detailsAvailable).containsExactly(true, false);
    }

    @Test
    void history_isEmptyWhenThereAreNoAttempts() {
        when(attemptRepository.findPage(eq(5L), ArgumentMatchers.<Pageable>any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        PageResponse<ExamAttemptResponse> history = service.history(PageParams.ofList(null, null), authentication);

        assertThat(history.items()).isEmpty();
        assertThat(history.totalElements()).isZero();
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
