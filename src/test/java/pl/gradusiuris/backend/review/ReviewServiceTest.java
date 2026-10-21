package pl.gradusiuris.backend.review;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import pl.gradusiuris.backend.common.exception.ResourceNotFoundException;
import pl.gradusiuris.backend.flashcard.FlashcardService;
import pl.gradusiuris.backend.flashcard.FlashcardStatus;
import pl.gradusiuris.backend.question.CorrectOption;
import pl.gradusiuris.backend.question.Question;
import pl.gradusiuris.backend.question.QuestionRepository;
import pl.gradusiuris.backend.review.dto.SubmitReviewRequest;
import pl.gradusiuris.backend.review.dto.SubmitTestAnswerRequest;
import pl.gradusiuris.backend.review.dto.TestAnswerResultResponse;
import pl.gradusiuris.backend.review.fsrs.FsrsEngine;
import pl.gradusiuris.backend.review.fsrs.FsrsWeights;
import pl.gradusiuris.backend.settings.EffectiveSettings;
import pl.gradusiuris.backend.settings.LearningSettingsService;
import pl.gradusiuris.backend.testsupport.MutableClock;
import pl.gradusiuris.backend.user.CurrentUserResolver;
import pl.gradusiuris.backend.user.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    // 2026-10-12 12:00 czasu warszawskiego
    private final MutableClock clock = MutableClock.at("2026-10-12T10:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 12, 0);

    @Mock private UserQuestionStateRepository stateRepository;
    @Mock private ReviewLogRepository reviewLogRepository;
    @Mock private QuestionRepository questionRepository;
    @Mock private CurrentUserResolver currentUserResolver;
    @Mock private FlashcardService flashcardService;
    @Mock private LearningSettingsService settingsService;
    @Mock private CustomTestSessionService customTestSessions;
    private final Authentication authentication = mock(Authentication.class);

    private ReviewService service;
    private User user;
    private Question question;

    @BeforeEach
    void setUp() {
        service = new ReviewService(stateRepository, reviewLogRepository, questionRepository, currentUserResolver,
                flashcardService, new FsrsEngine(FsrsWeights.defaults(), 0.9), settingsService,
                new IntervalFuzzer(new Random(3)), new LeechPolicy(8), customTestSessions, clock);
        user = new User();
        user.setId(5L);
        question = new Question();
        question.setId(11L);
        question.setCorrectOption(CorrectOption.B);
        question.setExplanation("Bo tak.");
        question.setLegalBasis("art. 1 k.c.");
        question.setValidAsOf(LocalDate.of(2026, 1, 1));

        when(currentUserResolver.resolve(authentication)).thenReturn(user);
        when(questionRepository.findById(11L)).thenReturn(Optional.of(question));
    }

    private void settings(double retention, Long daysToExam) {
        double effective = SchedulingRules.effectiveRetention(retention, daysToExam);
        when(settingsService.effectiveFor(5L)).thenReturn(new EffectiveSettings(
                20, 40, 200, retention, effective, null,
                daysToExam == null ? null : LocalDate.of(2026, 10, 12).plusDays(daysToExam), daysToExam, false));
    }

    private UserQuestionState matureState(ReviewMode mode, double stability, int daysSinceLastReview) {
        UserQuestionState state = new UserQuestionState();
        state.setUser(user);
        state.setQuestion(question);
        state.setMode(mode);
        state.setStability(stability);
        state.setDifficulty(5.0);
        state.setReps(4);
        state.setLastReviewAt(NOW.minusDays(daysSinceLastReview));
        state.setDueDate(NOW.minusHours(1));
        state.setState(LearningState.REVIEW);
        return state;
    }

    private void existing(ReviewMode mode, UserQuestionState state) {
        when(stateRepository.findByUserIdAndQuestionIdAndMode(5L, 11L, mode)).thenReturn(Optional.ofNullable(state));
    }

    private UserQuestionState savedState() {
        ArgumentCaptor<UserQuestionState> captor = ArgumentCaptor.forClass(UserQuestionState.class);
        verify(stateRepository).save(captor.capture());
        return captor.getValue();
    }

    // ---------------------------------------------------------------- pierwsza powtórka i logi

    @Test
    void firstFlashcardReview_createsStateAndLog() {
        settings(0.9, null);
        existing(ReviewMode.FLASHCARD, null);

        boolean first = service.submitReview(new SubmitReviewRequest(11L, 3, 6.5), authentication);

        assertThat(first).isTrue();
        UserQuestionState state = savedState();
        assertThat(state.getMode()).isEqualTo(ReviewMode.FLASHCARD);
        assertThat(state.getQuestion()).isSameAs(question);
        assertThat(state.getReps()).isEqualTo(1);
        assertThat(state.getLapses()).isEqualTo(0);
        assertThat(state.getState()).isEqualTo(LearningState.REVIEW);
        assertThat(state.getLastReviewAt()).isEqualTo(NOW);
        assertThat(state.getDueDate()).isAfter(NOW);

        ArgumentCaptor<ReviewLog> log = ArgumentCaptor.forClass(ReviewLog.class);
        verify(reviewLogRepository).save(log.capture());
        assertThat(log.getValue().getMode()).isEqualTo(ReviewMode.FLASHCARD);
        assertThat(log.getValue().getQuestion()).isSameAs(question);
        assertThat(log.getValue().getRating()).isEqualTo(3);
        assertThat(log.getValue().getElapsedDays()).isNull();
        assertThat(log.getValue().getWasCorrect()).isNull();
    }

    @Test
    void secondFlashcardReview_isNotReportedAsTheFirstOne() {
        settings(0.9, null);
        existing(ReviewMode.FLASHCARD, matureState(ReviewMode.FLASHCARD, 10, 5));

        boolean first = service.submitReview(new SubmitReviewRequest(11L, 3, null), authentication);

        assertThat(first).isFalse();
    }

    @Test
    void firstFlashcardReview_againCountsAsLapse() {
        settings(0.9, null);
        existing(ReviewMode.FLASHCARD, null);

        service.submitReview(new SubmitReviewRequest(11L, 1, null), authentication);

        UserQuestionState state = savedState();
        assertThat(state.getLapses()).isEqualTo(1);
        assertThat(state.getState()).isEqualTo(LearningState.LEARNING);
        // krótki odstęp: wraca jeszcze tego samego dnia
        assertThat(state.getDueDate()).isBefore(NOW.toLocalDate().plusDays(1).atStartOfDay());
    }

    // ---------------------------------------------------------------- retencja i tryb egzaminacyjny

    @Test
    void higherRetention_bringsTheQuestionBackSooner() {
        settings(0.97, null);
        UserQuestionState careful = matureState(ReviewMode.FLASHCARD, 60, 20);
        existing(ReviewMode.FLASHCARD, careful);
        service.submitReview(new SubmitReviewRequest(11L, 3, null), authentication);
        LocalDateTime carefulDue = careful.getDueDate();

        org.mockito.Mockito.reset(settingsService, stateRepository);
        settings(0.80, null);
        UserQuestionState relaxed = matureState(ReviewMode.FLASHCARD, 60, 20);
        existing(ReviewMode.FLASHCARD, relaxed);
        service.submitReview(new SubmitReviewRequest(11L, 3, null), authentication);

        assertThat(carefulDue).isBefore(relaxed.getDueDate());
    }

    @Test
    void examInAWeek_capsTheIntervalSoTheQuestionReturnsBeforeTheExam() {
        settings(0.9, 7L);
        UserQuestionState state = matureState(ReviewMode.FLASHCARD, 120, 20); // bez egzaminu odstęp byłby rzędu miesięcy
        existing(ReviewMode.FLASHCARD, state);

        service.submitReview(new SubmitReviewRequest(11L, 3, null), authentication);

        // egzamin za 7 dni: pytanie wraca najpóźniej 6 dni od dziś, na początku dnia
        assertThat(state.getDueDate()).isEqualTo(NOW.toLocalDate().plusDays(6).atStartOfDay());
    }

    @Test
    void noExam_leavesLongIntervalsAlone() {
        settings(0.9, null);
        UserQuestionState state = matureState(ReviewMode.FLASHCARD, 120, 20);
        existing(ReviewMode.FLASHCARD, state);

        service.submitReview(new SubmitReviewRequest(11L, 3, null), authentication);

        assertThat(state.getDueDate()).isAfter(NOW.plusDays(30));
    }

    // ---------------------------------------------------------------- ochrona przed podwójnym wysłaniem

    @Test
    void duplicateSubmitWithinASecond_isIgnored() {
        UserQuestionState state = matureState(ReviewMode.FLASHCARD, 10, 0);
        state.setLastReviewAt(NOW.minusNanos(400_000_000));
        existing(ReviewMode.FLASHCARD, state);

        service.submitReview(new SubmitReviewRequest(11L, 3, null), authentication);

        verify(stateRepository, never()).save(any());
        verify(reviewLogRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- fiszka: reguły dostępności

    @Test
    void flashcardRatingOfAHiddenFlashcard_isRejected() {
        question.setFlashcardHidden(true);

        assertThatThrownBy(() -> service.submitReview(new SubmitReviewRequest(11L, 3, 5.0), authentication))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nie ma fiszki");

        verify(stateRepository, never()).save(any());
        verify(reviewLogRepository, never()).save(any());
    }

    @Test
    void missingQuestion_isNotFound() {
        when(questionRepository.findById(11L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitReview(new SubmitReviewRequest(11L, 3, null), authentication))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.submitTestAnswer(
                new SubmitTestAnswerRequest(11L, CorrectOption.A, null, null, null), authentication))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(stateRepository, never()).save(any());
    }

    @Test
    void hiddenFlashcardDoesNotBlockAnswersInTests() {
        // ukryta fiszka nie dotyczy testu: pytanie nadal można rozwiązywać
        question.setFlashcardHidden(true);
        settings(0.9, null);
        existing(ReviewMode.TEST, null);
        when(flashcardService.statusOf(5L, question, false)).thenReturn(FlashcardStatus.UNAVAILABLE);

        TestAnswerResultResponse result = service.submitTestAnswer(
                new SubmitTestAnswerRequest(11L, CorrectOption.B, 3.0, false, null), authentication);

        assertThat(result.correct()).isTrue();
        assertThat(result.flashcard()).isEqualTo(FlashcardStatus.UNAVAILABLE);
    }

    // ---------------------------------------------------------------- osobne stany testu i fiszki

    @Test
    void testAnswer_touchesOnlyTheTestState_neverTheFlashcardOne() {
        settings(0.9, null);
        existing(ReviewMode.TEST, null);

        service.submitTestAnswer(new SubmitTestAnswerRequest(11L, CorrectOption.A, 2.0, false, null), authentication);

        verify(stateRepository, never()).findByUserIdAndQuestionIdAndMode(5L, 11L, ReviewMode.FLASHCARD);
        UserQuestionState saved = savedState();
        assertThat(saved.getMode()).isEqualTo(ReviewMode.TEST);

        ArgumentCaptor<ReviewLog> log = ArgumentCaptor.forClass(ReviewLog.class);
        verify(reviewLogRepository).save(log.capture());
        assertThat(log.getValue().getMode()).isEqualTo(ReviewMode.TEST);
        assertThat(log.getValue().getWasCorrect()).isFalse();
        assertThat(log.getValue().isWasGuessed()).isFalse();
    }

    @Test
    void flashcardReview_touchesOnlyTheFlashcardState_neverTheTestOne() {
        settings(0.9, null);
        existing(ReviewMode.FLASHCARD, null);

        service.submitReview(new SubmitReviewRequest(11L, 1, null), authentication);

        verify(stateRepository, never()).findByUserIdAndQuestionIdAndMode(5L, 11L, ReviewMode.TEST);
        assertThat(savedState().getMode()).isEqualTo(ReviewMode.FLASHCARD);
    }

    @Test
    void testAnswerFirstTime_isReportedAsANewQuestion() {
        settings(0.9, null);
        existing(ReviewMode.TEST, null);

        TestAnswerResultResponse result = service.submitTestAnswer(
                new SubmitTestAnswerRequest(11L, CorrectOption.B, 4.0, false, null), authentication);

        assertThat(result.newQuestion()).isTrue();
    }

    @Test
    void testAnswerAgain_isNotANewQuestion() {
        settings(0.9, null);
        existing(ReviewMode.TEST, matureState(ReviewMode.TEST, 10, 5));

        TestAnswerResultResponse result = service.submitTestAnswer(
                new SubmitTestAnswerRequest(11L, CorrectOption.B, 4.0, false, null), authentication);

        assertThat(result.newQuestion()).isFalse();
    }

    @Test
    void testAnswerInASavedCustomTest_isRecordedThere() {
        settings(0.9, null);
        existing(ReviewMode.TEST, matureState(ReviewMode.TEST, 10, 5));

        service.submitTestAnswer(new SubmitTestAnswerRequest(11L, CorrectOption.A, 4.0, true, 77L), authentication);

        verify(customTestSessions).recordAnswer(5L, 77L, 11L, CorrectOption.A, false, true, 4.0);
    }

    // ---------------------------------------------------------------- test ABC

    @Test
    void wrongTestAnswer_isALapse_andRevealsExplanationAndLegalBasis() {
        settings(0.9, null);
        UserQuestionState state = matureState(ReviewMode.TEST, 10, 5);
        existing(ReviewMode.TEST, state);

        TestAnswerResultResponse result = service.submitTestAnswer(
                new SubmitTestAnswerRequest(11L, CorrectOption.A, 4.0, false, null), authentication);

        assertThat(result.correct()).isFalse();
        assertThat(result.correctOption()).isEqualTo(CorrectOption.B);
        assertThat(result.explanation()).isEqualTo("Bo tak.");
        assertThat(result.legalBasis()).isEqualTo("art. 1 k.c.");
        assertThat(result.validAsOf()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(state.getLapses()).isEqualTo(1);
        assertThat(state.getState()).isEqualTo(LearningState.RELEARNING);
    }

    @Test
    void confidentCorrectTestAnswer_isNotALapse() {
        settings(0.9, null);
        UserQuestionState state = matureState(ReviewMode.TEST, 10, 5);
        existing(ReviewMode.TEST, state);

        TestAnswerResultResponse result = service.submitTestAnswer(
                new SubmitTestAnswerRequest(11L, CorrectOption.B, 4.0, false, null), authentication);

        assertThat(result.correct()).isTrue();
        assertThat(state.getLapses()).isEqualTo(0);
        assertThat(state.getState()).isEqualTo(LearningState.REVIEW);
    }

    @Test
    void correctButUnsureTestAnswer_isNotALapse_butComesBackSooner() {
        settings(0.9, null);
        UserQuestionState sure = matureState(ReviewMode.TEST, 30, 20);
        existing(ReviewMode.TEST, sure);
        service.submitTestAnswer(new SubmitTestAnswerRequest(11L, CorrectOption.B, 4.0, false, null), authentication);
        LocalDateTime sureDue = sure.getDueDate();

        org.mockito.Mockito.reset(stateRepository);
        UserQuestionState unsure = matureState(ReviewMode.TEST, 30, 20);
        existing(ReviewMode.TEST, unsure);
        service.submitTestAnswer(new SubmitTestAnswerRequest(11L, CorrectOption.B, 4.0, true, null), authentication);

        assertThat(unsure.getLapses()).isZero();
        assertThat(unsure.getDueDate()).isBefore(sureDue);
    }

    // ---------------------------------------------------------------- trudne pytania

    @Test
    void wrongAnswer_reachingTheThreshold_marksTheQuestionAsDifficult() {
        settings(0.9, null);
        UserQuestionState state = matureState(ReviewMode.TEST, 10, 5);
        state.setLapses(7);
        existing(ReviewMode.TEST, state);

        TestAnswerResultResponse result = service.submitTestAnswer(
                new SubmitTestAnswerRequest(11L, CorrectOption.A, 4.0, false, null), authentication);

        assertThat(result.correct()).isFalse();
        assertThat(result.lapses()).isEqualTo(8);
        assertThat(result.leech()).isTrue();
        assertThat(state.isSuspended()).as("aplikacja niczego nie zawiesza sama").isFalse();
    }

    @Test
    void wrongAnswer_belowTheThreshold_isNotYetDifficult() {
        settings(0.9, null);
        UserQuestionState state = matureState(ReviewMode.TEST, 10, 5);
        state.setLapses(6);
        existing(ReviewMode.TEST, state);

        TestAnswerResultResponse result = service.submitTestAnswer(
                new SubmitTestAnswerRequest(11L, CorrectOption.A, 4.0, false, null), authentication);

        assertThat(result.lapses()).isEqualTo(7);
        assertThat(result.leech()).isFalse();
    }

    @Test
    void correctAnswer_onAnAlreadyDifficultQuestion_stillReportsItAsDifficult() {
        settings(0.9, null);
        UserQuestionState state = matureState(ReviewMode.TEST, 10, 5);
        state.setLapses(9);
        existing(ReviewMode.TEST, state);

        TestAnswerResultResponse result = service.submitTestAnswer(
                new SubmitTestAnswerRequest(11L, CorrectOption.B, 4.0, false, null), authentication);

        assertThat(result.correct()).isTrue();
        assertThat(result.lapses()).isEqualTo(9);
        assertThat(result.leech()).isTrue();
    }

    @Test
    void suspendedQuestion_canStillBeAnswered_andStaysSuspended() {
        settings(0.9, null);
        UserQuestionState state = matureState(ReviewMode.TEST, 10, 5);
        state.setSuspended(true);
        state.setSuspendedAt(NOW.minusDays(1));
        existing(ReviewMode.TEST, state);

        service.submitTestAnswer(new SubmitTestAnswerRequest(11L, CorrectOption.B, 4.0, false, null), authentication);

        assertThat(state.getReps()).isEqualTo(5);
        assertThat(state.isSuspended()).isTrue();
    }

    @Test
    void emptyExplanationIsFine_theAnswerStillWorks() {
        settings(0.9, null);
        question.setExplanation(null);
        question.setLegalBasis(null);
        question.setValidAsOf(null);
        existing(ReviewMode.TEST, null);

        TestAnswerResultResponse result = service.submitTestAnswer(
                new SubmitTestAnswerRequest(11L, CorrectOption.B, null, null, null), authentication);

        assertThat(result.correct()).isTrue();
        assertThat(result.explanation()).isNull();
        assertThat(result.legalBasis()).isNull();
    }

    // ---------------------------------------------------------------- odblokowanie fiszek

    @Test
    void firstConfidentMistake_addsTheFlashcardAutomatically_andAnnouncesIt() {
        settings(0.9, null);
        existing(ReviewMode.TEST, null);
        when(reviewLogRepository.countConfidentMistakes(5L, 11L)).thenReturn(0L);
        when(flashcardService.statusOf(5L, question, true)).thenReturn(FlashcardStatus.ADDED_BY_MISTAKE);

        TestAnswerResultResponse result = service.submitTestAnswer(
                new SubmitTestAnswerRequest(11L, CorrectOption.A, 4.0, false, null), authentication);

        assertThat(result.correct()).isFalse();
        assertThat(result.flashcardUnlocked()).isTrue();
        assertThat(result.flashcard()).isEqualTo(FlashcardStatus.ADDED_BY_MISTAKE);
    }

    @Test
    void secondConfidentMistake_doesNotAnnounceTheUnlockAgain_butTheFlashcardStaysAdded() {
        settings(0.9, null);
        existing(ReviewMode.TEST, matureState(ReviewMode.TEST, 10, 5));
        when(reviewLogRepository.countConfidentMistakes(5L, 11L)).thenReturn(1L);
        when(flashcardService.statusOf(5L, question, true)).thenReturn(FlashcardStatus.ADDED_BY_MISTAKE);

        TestAnswerResultResponse result = service.submitTestAnswer(
                new SubmitTestAnswerRequest(11L, CorrectOption.A, 4.0, false, null), authentication);

        assertThat(result.flashcardUnlocked()).isFalse();
        assertThat(result.flashcard()).isEqualTo(FlashcardStatus.ADDED_BY_MISTAKE);
    }

    @Test
    void mistakeMarkedAsUnsure_doesNotAddTheFlashcard_butOffersTheButton() {
        settings(0.9, null);
        existing(ReviewMode.TEST, matureState(ReviewMode.TEST, 10, 5));
        when(reviewLogRepository.countConfidentMistakes(5L, 11L)).thenReturn(0L);
        when(flashcardService.statusOf(5L, question, false)).thenReturn(FlashcardStatus.AVAILABLE);

        TestAnswerResultResponse result = service.submitTestAnswer(
                new SubmitTestAnswerRequest(11L, CorrectOption.A, 4.0, true, null), authentication);

        assertThat(result.correct()).isFalse();
        assertThat(result.flashcardUnlocked()).isFalse();
        assertThat(result.flashcard()).isEqualTo(FlashcardStatus.AVAILABLE);
    }

    @Test
    void mistakeMarkedAsUnsure_afterAnEarlierConfidentMistake_keepsTheFlashcardAdded() {
        settings(0.9, null);
        existing(ReviewMode.TEST, matureState(ReviewMode.TEST, 10, 5));
        when(reviewLogRepository.countConfidentMistakes(5L, 11L)).thenReturn(2L);
        when(flashcardService.statusOf(5L, question, true)).thenReturn(FlashcardStatus.ADDED_BY_MISTAKE);

        TestAnswerResultResponse result = service.submitTestAnswer(
                new SubmitTestAnswerRequest(11L, CorrectOption.A, 4.0, true, null), authentication);

        assertThat(result.flashcardUnlocked()).isFalse();
        assertThat(result.flashcard()).isEqualTo(FlashcardStatus.ADDED_BY_MISTAKE);
    }

    @Test
    void correctAnswer_neverAddsAFlashcardByItself_butOffersTheButton() {
        settings(0.9, null);
        existing(ReviewMode.TEST, matureState(ReviewMode.TEST, 10, 5));
        when(reviewLogRepository.countConfidentMistakes(5L, 11L)).thenReturn(0L);
        when(flashcardService.statusOf(5L, question, false)).thenReturn(FlashcardStatus.AVAILABLE);

        TestAnswerResultResponse result = service.submitTestAnswer(
                new SubmitTestAnswerRequest(11L, CorrectOption.B, 4.0, false, null), authentication);

        assertThat(result.correct()).isTrue();
        assertThat(result.flashcardUnlocked()).isFalse();
        assertThat(result.flashcard()).isEqualTo(FlashcardStatus.AVAILABLE);
    }

    @Test
    void alreadyAddedManually_isReportedSoTheUiCanOfferTheUndo() {
        settings(0.9, null);
        existing(ReviewMode.TEST, matureState(ReviewMode.TEST, 10, 5));
        when(reviewLogRepository.countConfidentMistakes(5L, 11L)).thenReturn(0L);
        when(flashcardService.statusOf(5L, question, false)).thenReturn(FlashcardStatus.ADDED_MANUALLY);

        TestAnswerResultResponse result = service.submitTestAnswer(
                new SubmitTestAnswerRequest(11L, CorrectOption.B, 4.0, false, null), authentication);

        assertThat(result.flashcard()).isEqualTo(FlashcardStatus.ADDED_MANUALLY);
    }

    @Test
    void confidentMistake_onAQuestionWithoutAFlashcard_unlocksNothing() {
        settings(0.9, null);
        existing(ReviewMode.TEST, matureState(ReviewMode.TEST, 10, 5));
        when(reviewLogRepository.countConfidentMistakes(5L, 11L)).thenReturn(0L);
        when(flashcardService.statusOf(5L, question, true)).thenReturn(FlashcardStatus.UNAVAILABLE);

        TestAnswerResultResponse result = service.submitTestAnswer(
                new SubmitTestAnswerRequest(11L, CorrectOption.A, 4.0, false, null), authentication);

        assertThat(result.flashcardUnlocked()).isFalse();
        assertThat(result.flashcard()).isEqualTo(FlashcardStatus.UNAVAILABLE);
    }
}
