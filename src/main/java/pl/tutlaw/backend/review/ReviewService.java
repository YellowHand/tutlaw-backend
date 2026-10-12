package pl.tutlaw.backend.review;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tutlaw.backend.card.Card;
import pl.tutlaw.backend.card.CardRepository;
import pl.tutlaw.backend.common.exception.ResourceNotFoundException;
import pl.tutlaw.backend.review.dto.SubmitReviewRequest;
import pl.tutlaw.backend.review.dto.SubmitTestAnswerRequest;
import pl.tutlaw.backend.review.dto.TestAnswerResultResponse;
import pl.tutlaw.backend.review.fsrs.FsrsCardState;
import pl.tutlaw.backend.review.fsrs.FsrsEngine;
import pl.tutlaw.backend.review.fsrs.Rating;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final UserCardStateRepository stateRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final CardRepository cardRepository;
    private final CurrentUserResolver currentUserResolver;
    private final FsrsEngine fsrsEngine;
    private final Clock clock;

    /** Ocena fiszki: 1–4 wybrana przez użytkownika. „Nie pamiętam" (1) liczy się jako błąd. */
    @Transactional
    public void submitReview(SubmitReviewRequest request, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        Card card = findCard(request.cardId());

        Rating rating = Rating.values()[request.rating() - 1];
        applyReview(user, card, rating, request.timeSpentSeconds(),
                ReviewMode.FLASHCARD, null, false, rating == Rating.AGAIN);
    }

    /**
     * Odpowiedź w teście. Ocena pamięci wynika z reguły {@link ReviewPolicy#testRating}:
     * pewna poprawna to „Znam", niepewna lub błędna to „Nie pamiętam".
     * Licznik błędów (lapses) rośnie tylko przy błędnej odpowiedzi, a nie przy poprawnej, ale niepewnej.
     */
    @Transactional
    public TestAnswerResultResponse submitTestAnswer(SubmitTestAnswerRequest request, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        Card card = findCard(request.cardId());

        if (card.getCorrectOption() == null) {
            throw new IllegalStateException("This card has no test format defined");
        }

        boolean correct = card.getCorrectOption() == request.selectedOption();
        boolean guessed = Boolean.TRUE.equals(request.guessed());

        applyReview(user, card, ReviewPolicy.testRating(correct, guessed), request.timeSpentSeconds(),
                ReviewMode.TEST, correct, guessed, !correct);

        return new TestAnswerResultResponse(correct, card.getCorrectOption(), card.getExplanation());
    }

    private Card findCard(Long cardId) {
        return cardRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Card not found"));
    }

    private void applyReview(
            User user, Card card, Rating rating, Double timeSpentSeconds,
            ReviewMode mode, Boolean wasCorrect, boolean wasGuessed, boolean countAsLapse) {

        UserCardState state = stateRepository.findByUserIdAndCardId(user.getId(), card.getId())
                .orElseGet(() -> {
                    UserCardState s = new UserCardState();
                    s.setUser(user);
                    s.setCard(card);
                    return s;
                });

        boolean firstReview = state.getReps() == 0;
        double stabilityBefore = state.getStability();
        LocalDateTime now = LocalDateTime.now(clock);
        Double elapsedDays = null;
        FsrsCardState newFsrsState;

        if (firstReview) {
            newFsrsState = fsrsEngine.firstReview(rating);
        } else {
            elapsedDays = Duration.between(state.getLastReviewAt(), now).toMillis() / 86_400_000.0;
            FsrsCardState current = new FsrsCardState(state.getStability(), state.getDifficulty());
            newFsrsState = fsrsEngine.nextState(current, elapsedDays, rating);
        }

        double intervalDays = fsrsEngine.nextIntervalDays(newFsrsState.stability());

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
        log.setCard(card);
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
    }

    private CardLearningState learningStateAfter(Rating rating, boolean firstReview) {
        if (rating != Rating.AGAIN) return CardLearningState.REVIEW;
        return firstReview ? CardLearningState.LEARNING : CardLearningState.RELEARNING;
    }

    private void updateRunningAverageTime(UserCardState state, Double newTime) {
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
