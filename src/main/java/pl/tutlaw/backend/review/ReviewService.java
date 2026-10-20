package pl.tutlaw.backend.review;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tutlaw.backend.common.exception.ResourceNotFoundException;
import pl.tutlaw.backend.flashcard.FlashcardService;
import pl.tutlaw.backend.flashcard.FlashcardStatus;
import pl.tutlaw.backend.question.Question;
import pl.tutlaw.backend.question.dto.LawChangeInfo;
import pl.tutlaw.backend.question.QuestionRepository;
import pl.tutlaw.backend.review.dto.SubmitReviewRequest;
import pl.tutlaw.backend.review.dto.SubmitTestAnswerRequest;
import pl.tutlaw.backend.review.dto.TestAnswerResultResponse;
import pl.tutlaw.backend.review.fsrs.FsrsCardState;
import pl.tutlaw.backend.review.fsrs.FsrsEngine;
import pl.tutlaw.backend.review.fsrs.Rating;
import pl.tutlaw.backend.settings.EffectiveSettings;
import pl.tutlaw.backend.settings.LearningSettingsService;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final UserQuestionStateRepository stateRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final QuestionRepository questionRepository;
    private final CurrentUserResolver currentUserResolver;
    private final FlashcardService flashcardService;
    private final FsrsEngine fsrsEngine;
    private final LearningSettingsService settingsService;
    private final IntervalFuzzer intervalFuzzer;
    private final LeechPolicy leechPolicy;
    private final CustomTestSessionService customTestSessions;
    private final Clock clock;

    /**
     * Ocena fiszki pytania: 1–4 wybrana przez użytkownika. „Nie pamiętam" (1) liczy się jako błąd.
     * Fiszka ma własny stan nauki, niezależny od stanu tego pytania w testach.
     *
     * @return czy to była pierwsza ocena tej fiszki (zaczęła się uczyć właśnie teraz)
     */
    @Transactional
    public boolean submitReview(SubmitReviewRequest request, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        Question question = findQuestion(request.questionId());

        if (question.isFlashcardHidden()) {
            throw new IllegalArgumentException("Z tego pytania nie ma fiszki");
        }

        Rating rating = Rating.values()[request.rating() - 1];
        UserQuestionState state = applyReview(user, question, rating, request.timeSpentSeconds(),
                ReviewMode.FLASHCARD, null, false, rating == Rating.AGAIN);
        return state.getReps() == 1;
    }

    /**
     * Odpowiedź w teście. Ocena pamięci wynika z reguły {@link ReviewPolicy#testRating}:
     * pewna poprawna to „Znam", niepewna lub błędna to „Nie pamiętam".
     * Licznik błędów (lapses) rośnie tylko przy błędnej odpowiedzi, a nie przy poprawnej, ale niepewnej.
     */
    @Transactional
    public TestAnswerResultResponse submitTestAnswer(SubmitTestAnswerRequest request, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        Question question = findQuestion(request.questionId());

        boolean correct = question.getCorrectOption() == request.selectedOption();
        boolean guessed = Boolean.TRUE.equals(request.guessed());

        // Pewny błąd (zły wybór bez „Nie jestem pewien") sam dodaje fiszkę pytania do Fiszek. Informujemy o tym
        // tylko raz: gdy to pierwszy taki błąd w tym pytaniu i fiszka istnieje (jest widoczna).
        boolean confidentMistake = !correct && !guessed;
        long earlierConfidentMistakes = reviewLogRepository.countConfidentMistakes(user.getId(), question.getId());
        FlashcardStatus flashcard = flashcardService.statusOf(
                user.getId(), question, confidentMistake || earlierConfidentMistakes > 0);
        boolean unlocksFlashcard = confidentMistake && earlierConfidentMistakes == 0
                && flashcard == FlashcardStatus.ADDED_BY_MISTAKE;

        UserQuestionState state = applyReview(user, question, ReviewPolicy.testRating(correct, guessed),
                request.timeSpentSeconds(), ReviewMode.TEST, correct, guessed, !correct);

        // Odpowiedź w zapisanym własnym teście ("Moje testy"). Powtórki, błędy i egzamin przychodzą bez numeru testu.
        customTestSessions.recordAnswer(user.getId(), request.sessionId(), question.getId(), request.selectedOption(),
                correct, guessed, request.timeSpentSeconds());

        return new TestAnswerResultResponse(
                correct, question.getCorrectOption(), question.getExplanation(), question.getLegalBasis(),
                question.getValidAsOf(), state.getLapses(), leechPolicy.isLeech(state.getLapses()),
                unlocksFlashcard, flashcard, state.getReps() == 1, LawChangeInfo.of(question));
    }

    /**
     * Pytanie musi istnieć. Ukrycie pytania NIE blokuje odpowiedzi: kto ma je już otwarte w sesji, kończy ją normalnie
     * (nowe sesje go nie zawierają). Pula użytkownika decyduje o tym, co mu się pokazuje, a nie o tym, co wolno
     * ocenić: wszystkie pytania są jawne dla każdego zalogowanego.
     */
    private Question findQuestion(Long questionId) {
        return questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found"));
    }

    /** Zapisuje odpowiedź i zwraca stan pytania w trybie po niej (przy zdublowanym żądaniu: stan bez zmian). */
    private UserQuestionState applyReview(
            User user, Question question, Rating rating, Double timeSpentSeconds,
            ReviewMode mode, Boolean wasCorrect, boolean wasGuessed, boolean countAsLapse) {

        UserQuestionState state = stateRepository.findByUserIdAndQuestionIdAndMode(user.getId(), question.getId(), mode)
                .orElseGet(() -> {
                    UserQuestionState s = new UserQuestionState();
                    s.setUser(user);
                    s.setQuestion(question);
                    s.setMode(mode);
                    return s;
                });

        boolean firstReview = state.getReps() == 0;
        double stabilityBefore = state.getStability();
        LocalDateTime now = LocalDateTime.now(clock);

        // Podwójne kliknięcie lub ponowione żądanie nie może policzyć się jako druga powtórka.
        if (!firstReview && ReviewPolicy.isDuplicateSubmit(state.getLastReviewAt(), now)) {
            return state;
        }
        Double elapsedDays = null;
        FsrsCardState newFsrsState;

        if (firstReview) {
            newFsrsState = fsrsEngine.firstReview(rating);
        } else {
            elapsedDays = Duration.between(state.getLastReviewAt(), now).toMillis() / 86_400_000.0;
            FsrsCardState current = new FsrsCardState(state.getStability(), state.getDifficulty());
            newFsrsState = fsrsEngine.nextState(current, elapsedDays, rating);
        }

        // Odstęp: retencja użytkownika (w ostatnich dniach przed egzaminem wyższa), potem rozrzut terminów,
        // a na końcu przycięcie tak, żeby karta wróciła przed egzaminem.
        EffectiveSettings settings = settingsService.effectiveFor(user.getId());
        double intervalDays = fsrsEngine.nextIntervalDays(newFsrsState.stability(), settings.effectiveRetention());
        intervalDays = SchedulingRules.capForExam(intervalFuzzer.apply(intervalDays), settings.daysToExam());

        state.setStability(newFsrsState.stability());
        state.setDifficulty(newFsrsState.difficulty());
        state.setDueDate(ReviewPolicy.computeDueDate(now, intervalDays));
        state.setReps(state.getReps() + 1);
        state.setLastReviewAt(now);
        state.setState(learningStateAfter(rating, firstReview));
        if (countAsLapse) {
            state.setLapses(state.getLapses() + 1);
        }
        updateRunningAverageTime(state, timeSpentSeconds);

        stateRepository.save(state);

        ReviewLog log = new ReviewLog();
        log.setUser(user);
        log.setQuestion(question);
        log.setRating(rating.getValue());
        log.setReviewedAt(now);
        log.setElapsedDays(elapsedDays);
        log.setStabilityBefore(stabilityBefore);
        log.setStabilityAfter(newFsrsState.stability());
        log.setTimeSpentSeconds(timeSpentSeconds);
        log.setMode(mode);
        log.setWasCorrect(wasCorrect);
        log.setWasGuessed(wasGuessed);
        reviewLogRepository.save(log);
        return state;
    }

    private LearningState learningStateAfter(Rating rating, boolean firstReview) {
        if (rating != Rating.AGAIN) return LearningState.REVIEW;
        return firstReview ? LearningState.LEARNING : LearningState.RELEARNING;
    }

    private void updateRunningAverageTime(UserQuestionState state, Double newTime) {
        if (newTime == null) return;
        if (state.getAvgTimeSeconds() == null) {
            state.setAvgTimeSeconds(newTime);
        } else {
            int n = state.getReps();
            double updatedAvg = state.getAvgTimeSeconds() + (newTime - state.getAvgTimeSeconds()) / n;
            state.setAvgTimeSeconds(updatedAvg);
        }
    }
}
