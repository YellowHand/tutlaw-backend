package pl.tutlaw.backend.review;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import pl.tutlaw.backend.card.Card;
import pl.tutlaw.backend.card.CardRepository;
import pl.tutlaw.backend.card.CorrectOption;
import pl.tutlaw.backend.common.exception.ResourceNotFoundException;
import pl.tutlaw.backend.review.dto.SubmitReviewRequest;
import pl.tutlaw.backend.review.dto.SubmitTestAnswerRequest;
import pl.tutlaw.backend.review.dto.TestAnswerResultResponse;
import pl.tutlaw.backend.review.fsrs.FsrsEngine;
import pl.tutlaw.backend.review.fsrs.FsrsWeights;
import pl.tutlaw.backend.settings.EffectiveSettings;
import pl.tutlaw.backend.settings.LearningSettingsService;
import pl.tutlaw.backend.testsupport.MutableClock;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

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

    @Mock private UserCardStateRepository stateRepository;
    @Mock private ReviewLogRepository reviewLogRepository;
    @Mock private CardRepository cardRepository;
    @Mock private CurrentUserResolver currentUserResolver;
    @Mock private LearningSettingsService settingsService;
    private final Authentication authentication = mock(Authentication.class);

    private ReviewService service;
    private User user;
    private Card card;

    @BeforeEach
    void setUp() {
        service = new ReviewService(stateRepository, reviewLogRepository, cardRepository, currentUserResolver,
                new FsrsEngine(FsrsWeights.defaults(), 0.9), settingsService,
                new IntervalFuzzer(new Random(3)), new LeechPolicy(8), clock);
        user = new User();
        user.setId(5L);
        card = new Card();
        card.setId(11L);
        card.setCorrectOption(CorrectOption.B);
        card.setExplanation("Bo tak.");
        card.setLegalBasis("art. 1 k.c.");
        card.setValidAsOf(LocalDate.of(2026, 1, 1));

        when(currentUserResolver.resolve(authentication)).thenReturn(user);
        when(cardRepository.findById(11L)).thenReturn(Optional.of(card));
        when(cardRepository.countReadableById(11L, 5L)).thenReturn(1L);
    }

    /** Fixture domyślnie jest pytaniem testowym; fiszka to karta bez poprawnej opcji. */
    private void asFlashcard() {
        card.setCorrectOption(null);
    }

    private void settings(double retention, Long daysToExam) {
        double effective = SchedulingRules.effectiveRetention(retention, daysToExam);
        when(settingsService.effectiveFor(5L)).thenReturn(new EffectiveSettings(
                20, 40, 200, retention, effective,
                daysToExam == null ? null : LocalDate.of(2026, 10, 12).plusDays(daysToExam), daysToExam, true));
    }

    private UserCardState matureState(double stability, int daysSinceLastReview) {
        UserCardState state = new UserCardState();
        state.setUser(user);
        state.setCard(card);
        state.setStability(stability);
        state.setDifficulty(5.0);
        state.setReps(4);
        state.setLastReviewAt(NOW.minusDays(daysSinceLastReview));
        state.setDueDate(NOW.minusHours(1));
        state.setState(CardLearningState.REVIEW);
        return state;
    }

    private UserCardState savedState() {
        ArgumentCaptor<UserCardState> captor = ArgumentCaptor.forClass(UserCardState.class);
        verify(stateRepository).save(captor.capture());
        return captor.getValue();
    }

    // ---------------------------------------------------------------- pierwsza powtórka i logi

    @Test
    void firstFlashcardReview_createsStateAndLog() {
        asFlashcard();
        settings(0.9, null);
        when(stateRepository.findByUserIdAndCardId(5L, 11L)).thenReturn(Optional.empty());

        service.submitReview(new SubmitReviewRequest(11L, 3, 6.5), authentication);

        UserCardState state = savedState();
        assertThat(state.getReps()).isEqualTo(1);
        assertThat(state.getLapses()).isEqualTo(0);
        assertThat(state.getState()).isEqualTo(CardLearningState.REVIEW);
        assertThat(state.getLastReviewAt()).isEqualTo(NOW);
        assertThat(state.getDueDate()).isAfter(NOW);

        ArgumentCaptor<ReviewLog> log = ArgumentCaptor.forClass(ReviewLog.class);
        verify(reviewLogRepository).save(log.capture());
        assertThat(log.getValue().getMode()).isEqualTo(ReviewMode.FLASHCARD);
        assertThat(log.getValue().getRating()).isEqualTo(3);
        assertThat(log.getValue().getElapsedDays()).isNull();
    }

    @Test
    void firstFlashcardReview_againCountsAsLapse() {
        asFlashcard();
        settings(0.9, null);
        when(stateRepository.findByUserIdAndCardId(5L, 11L)).thenReturn(Optional.empty());

        service.submitReview(new SubmitReviewRequest(11L, 1, null), authentication);

        UserCardState state = savedState();
        assertThat(state.getLapses()).isEqualTo(1);
        assertThat(state.getState()).isEqualTo(CardLearningState.LEARNING);
        // krótki odstęp: wraca jeszcze tego samego dnia
        assertThat(state.getDueDate()).isBefore(NOW.toLocalDate().plusDays(1).atStartOfDay());
    }

    // ---------------------------------------------------------------- retencja i tryb egzaminacyjny

    @Test
    void higherRetention_bringsTheCardBackSooner() {
        asFlashcard();
        settings(0.97, null);
        UserCardState careful = matureState(60, 20);
        when(stateRepository.findByUserIdAndCardId(5L, 11L)).thenReturn(Optional.of(careful));
        service.submitReview(new SubmitReviewRequest(11L, 3, null), authentication);
        LocalDateTime carefulDue = careful.getDueDate();

        org.mockito.Mockito.reset(settingsService, stateRepository);
        settings(0.80, null);
        UserCardState relaxed = matureState(60, 20);
        when(stateRepository.findByUserIdAndCardId(5L, 11L)).thenReturn(Optional.of(relaxed));
        service.submitReview(new SubmitReviewRequest(11L, 3, null), authentication);

        assertThat(carefulDue).isBefore(relaxed.getDueDate());
    }

    @Test
    void examInAWeek_capsTheIntervalSoTheCardReturnsBeforeTheExam() {
        asFlashcard();
        settings(0.9, 7L);
        UserCardState state = matureState(120, 20); // bez egzaminu odstęp byłby rzędu miesięcy
        when(stateRepository.findByUserIdAndCardId(5L, 11L)).thenReturn(Optional.of(state));

        service.submitReview(new SubmitReviewRequest(11L, 3, null), authentication);

        // egzamin za 7 dni: karta wraca najpóźniej 6 dni od dziś, na początku dnia
        assertThat(state.getDueDate()).isEqualTo(NOW.toLocalDate().plusDays(6).atStartOfDay());
    }

    @Test
    void noExam_leavesLongIntervalsAlone() {
        asFlashcard();
        settings(0.9, null);
        UserCardState state = matureState(120, 20);
        when(stateRepository.findByUserIdAndCardId(5L, 11L)).thenReturn(Optional.of(state));

        service.submitReview(new SubmitReviewRequest(11L, 3, null), authentication);

        assertThat(state.getDueDate()).isAfter(NOW.plusDays(30));
    }

    // ---------------------------------------------------------------- ochrona przed podwójnym wysłaniem

    @Test
    void duplicateSubmitWithinASecond_isIgnored() {
        asFlashcard();
        UserCardState state = matureState(10, 0);
        state.setLastReviewAt(NOW.minusNanos(400_000_000));
        when(stateRepository.findByUserIdAndCardId(5L, 11L)).thenReturn(Optional.of(state));

        service.submitReview(new SubmitReviewRequest(11L, 3, null), authentication);

        verify(stateRepository, never()).save(any());
        verify(reviewLogRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- test ABC

    @Test
    void wrongTestAnswer_isALapse_andRevealsExplanationAndLegalBasis() {
        settings(0.9, null);
        UserCardState state = matureState(10, 5);
        when(stateRepository.findByUserIdAndCardId(5L, 11L)).thenReturn(Optional.of(state));

        TestAnswerResultResponse result = service.submitTestAnswer(
                new SubmitTestAnswerRequest(11L, CorrectOption.A, 4.0, false), authentication);

        assertThat(result.correct()).isFalse();
        assertThat(result.correctOption()).isEqualTo(CorrectOption.B);
        assertThat(result.explanation()).isEqualTo("Bo tak.");
        assertThat(result.legalBasis()).isEqualTo("art. 1 k.c.");
        assertThat(result.validAsOf()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(state.getLapses()).isEqualTo(1);
        assertThat(state.getState()).isEqualTo(CardLearningState.RELEARNING);
    }

    @Test
    void confidentCorrectTestAnswer_isNotALapse() {
        settings(0.9, null);
        UserCardState state = matureState(10, 5);
        when(stateRepository.findByUserIdAndCardId(5L, 11L)).thenReturn(Optional.of(state));

        TestAnswerResultResponse result = service.submitTestAnswer(
                new SubmitTestAnswerRequest(11L, CorrectOption.B, 4.0, false), authentication);

        assertThat(result.correct()).isTrue();
        assertThat(state.getLapses()).isEqualTo(0);
        assertThat(state.getState()).isEqualTo(CardLearningState.REVIEW);
    }

    // ---------------------------------------------------------------- trudne pytania

    @Test
    void wrongAnswer_reachingTheThreshold_marksTheQuestionAsDifficult() {
        settings(0.9, null);
        UserCardState state = matureState(10, 5);
        state.setLapses(7);
        when(stateRepository.findByUserIdAndCardId(5L, 11L)).thenReturn(Optional.of(state));

        TestAnswerResultResponse result = service.submitTestAnswer(
                new SubmitTestAnswerRequest(11L, CorrectOption.A, 4.0, false), authentication);

        assertThat(result.correct()).isFalse();
        assertThat(result.lapses()).isEqualTo(8);
        assertThat(result.leech()).isTrue();
        assertThat(state.isSuspended()).as("aplikacja niczego nie zawiesza sama").isFalse();
    }

    @Test
    void wrongAnswer_belowTheThreshold_isNotYetDifficult() {
        settings(0.9, null);
        UserCardState state = matureState(10, 5);
        state.setLapses(6);
        when(stateRepository.findByUserIdAndCardId(5L, 11L)).thenReturn(Optional.of(state));

        TestAnswerResultResponse result = service.submitTestAnswer(
                new SubmitTestAnswerRequest(11L, CorrectOption.A, 4.0, false), authentication);

        assertThat(result.lapses()).isEqualTo(7);
        assertThat(result.leech()).isFalse();
    }

    @Test
    void correctAnswer_onAnAlreadyDifficultQuestion_stillReportsItAsDifficult() {
        settings(0.9, null);
        UserCardState state = matureState(10, 5);
        state.setLapses(9);
        when(stateRepository.findByUserIdAndCardId(5L, 11L)).thenReturn(Optional.of(state));

        TestAnswerResultResponse result = service.submitTestAnswer(
                new SubmitTestAnswerRequest(11L, CorrectOption.B, 4.0, false), authentication);

        assertThat(result.correct()).isTrue();
        assertThat(result.lapses()).isEqualTo(9);
        assertThat(result.leech()).isTrue();
    }

    @Test
    void suspendedQuestion_canStillBeAnswered_andStaysSuspended() {
        settings(0.9, null);
        UserCardState state = matureState(10, 5);
        state.setSuspended(true);
        state.setSuspendedAt(NOW.minusDays(1));
        when(stateRepository.findByUserIdAndCardId(5L, 11L)).thenReturn(Optional.of(state));

        service.submitTestAnswer(new SubmitTestAnswerRequest(11L, CorrectOption.B, 4.0, false), authentication);

        assertThat(state.getReps()).isEqualTo(5);
        assertThat(state.isSuspended()).isTrue();
    }

    @Test
    void emptyExplanationIsFine_theAnswerStillWorks() {
        settings(0.9, null);
        card.setExplanation(null);
        card.setLegalBasis(null);
        card.setValidAsOf(null);
        when(stateRepository.findByUserIdAndCardId(5L, 11L)).thenReturn(Optional.empty());

        TestAnswerResultResponse result = service.submitTestAnswer(
                new SubmitTestAnswerRequest(11L, CorrectOption.B, null, null), authentication);

        assertThat(result.correct()).isTrue();
        assertThat(result.explanation()).isNull();
        assertThat(result.legalBasis()).isNull();
    }

    @Test
    void testAnswerOnAFlashcardWithoutOptions_isRejected() {
        card.setCorrectOption(null);

        assertThatThrownBy(() -> service.submitTestAnswer(
                new SubmitTestAnswerRequest(11L, CorrectOption.A, null, null), authentication))
                .isInstanceOf(IllegalArgumentException.class);
        verify(stateRepository, never()).save(any());
    }

    @Test
    void unreadableCard_isTreatedAsMissing() {
        when(cardRepository.countReadableById(11L, 5L)).thenReturn(0L);

        assertThatThrownBy(() -> service.submitReview(new SubmitReviewRequest(11L, 3, null), authentication))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(stateRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- Fiszki i Testy osobno, odblokowanie fiszek

    @Test
    void flashcardRatingOfATestQuestion_isRejected_becauseTestQuestionsLiveOnlyInTests() {
        // card z fixture ma poprawną opcję B, czyli jest pytaniem testowym
        assertThatThrownBy(() -> service.submitReview(new SubmitReviewRequest(11L, 3, 5.0), authentication))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tylko w Testach");

        verify(stateRepository, never()).save(any());
        verify(reviewLogRepository, never()).save(any());
    }

    @Test
    void firstConfidentMistake_onAQuestionWithALinkedFlashcard_announcesTheUnlock() {
        settings(0.9, null);
        when(stateRepository.findByUserIdAndCardId(5L, 11L)).thenReturn(Optional.empty());
        when(reviewLogRepository.countConfidentMistakes(5L, 11L)).thenReturn(0L);
        when(cardRepository.existsByLinkedTestCardId(11L)).thenReturn(true);

        TestAnswerResultResponse result = service.submitTestAnswer(
                new SubmitTestAnswerRequest(11L, CorrectOption.A, 4.0, false), authentication);

        assertThat(result.correct()).isFalse();
        assertThat(result.flashcardUnlocked()).isTrue();
    }

    @Test
    void secondConfidentMistake_doesNotAnnounceTheUnlockAgain() {
        settings(0.9, null);
        UserCardState state = matureState(10, 5);
        when(stateRepository.findByUserIdAndCardId(5L, 11L)).thenReturn(Optional.of(state));
        when(reviewLogRepository.countConfidentMistakes(5L, 11L)).thenReturn(1L);

        TestAnswerResultResponse result = service.submitTestAnswer(
                new SubmitTestAnswerRequest(11L, CorrectOption.A, 4.0, false), authentication);

        assertThat(result.flashcardUnlocked()).isFalse();
    }

    @Test
    void mistakeMarkedAsUnsure_doesNotUnlockTheFlashcard_andSkipsTheLookups() {
        settings(0.9, null);
        UserCardState state = matureState(10, 5);
        when(stateRepository.findByUserIdAndCardId(5L, 11L)).thenReturn(Optional.of(state));

        TestAnswerResultResponse result = service.submitTestAnswer(
                new SubmitTestAnswerRequest(11L, CorrectOption.A, 4.0, true), authentication);

        assertThat(result.correct()).isFalse();
        assertThat(result.flashcardUnlocked()).isFalse();
        verify(reviewLogRepository, never()).countConfidentMistakes(any(), any());
    }

    @Test
    void correctAnswer_neverUnlocksAFlashcard() {
        settings(0.9, null);
        UserCardState state = matureState(10, 5);
        when(stateRepository.findByUserIdAndCardId(5L, 11L)).thenReturn(Optional.of(state));

        TestAnswerResultResponse result = service.submitTestAnswer(
                new SubmitTestAnswerRequest(11L, CorrectOption.B, 4.0, false), authentication);

        assertThat(result.flashcardUnlocked()).isFalse();
        verify(cardRepository, never()).existsByLinkedTestCardId(any());
    }

    @Test
    void confidentMistake_onAQuestionWithoutALinkedFlashcard_unlocksNothing() {
        settings(0.9, null);
        UserCardState state = matureState(10, 5);
        when(stateRepository.findByUserIdAndCardId(5L, 11L)).thenReturn(Optional.of(state));
        when(reviewLogRepository.countConfidentMistakes(5L, 11L)).thenReturn(0L);
        when(cardRepository.existsByLinkedTestCardId(11L)).thenReturn(false);

        TestAnswerResultResponse result = service.submitTestAnswer(
                new SubmitTestAnswerRequest(11L, CorrectOption.A, 4.0, false), authentication);

        assertThat(result.flashcardUnlocked()).isFalse();
    }
}
