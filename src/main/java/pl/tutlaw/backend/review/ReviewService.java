package pl.tutlaw.backend.review;

import jakarta.validation.Valid;
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

    @Transactional
    public void submitReview(@Valid SubmitReviewRequest request, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        Card card = cardRepository.findById(request.cardId())
                .orElseThrow(() -> new ResourceNotFoundException("Card not found"));

        Rating rating = Rating.values()[request.rating() - 1];

        UserCardState state = stateRepository.findByUserIdAndCardId(user.getId(), card.getId())
                .orElseGet(() -> {
                    UserCardState s = new UserCardState();
                    s.setUser(user);
                    s.setCard(card);
                    return s;
                });

        double stabilityBefore = state.getStability();
        LocalDateTime now = LocalDateTime.now();
        Double elapsedDays = null;
        FsrsCardState newFsrsState;

        if (state.getReps() == 0) {
            newFsrsState = fsrsEngine.firstReview(rating);
        } else {
            elapsedDays = Duration.between(state.getLastReviewAt(), now).toMillis() / 86_400_000.0;
            FsrsCardState current = new FsrsCardState(state.getStability(), state.getDifficulty());
            newFsrsState = fsrsEngine.nextState(current, elapsedDays, rating);
        }

        double intervalDays = fsrsEngine.nextIntervalDays(newFsrsState.stability());

        state.setStability(newFsrsState.stability());
        state.setDifficulty(newFsrsState.difficulty());
        state.setDueDate(now.plusSeconds((long) (intervalDays * 86400)));
        state.setReps(state.getReps() + 1);
        state.setLastReviewAt(now);
        state.setState(rating == Rating.AGAIN ? CardLearningState.RELEARNING : CardLearningState.REVIEW);
        if (rating == Rating.AGAIN) {
            state.setLapses(state.getLapses() + 1);
        }
        updateRunningAverageTime(state, request.timeSpentSeconds());

        stateRepository.save(state);

        ReviewLog log = new ReviewLog();
        log.setUser(user);
        log.setCard(card);
        log.setRating(rating.getValue());
        log.setReviewedAt(now);
        log.setElapsedDays(elapsedDays);
        log.setStabilityBefore(stabilityBefore);
        log.setStabilityAfter(newFsrsState.stability());
        log.setTimeSpentSeconds(request.timeSpentSeconds());
        reviewLogRepository.save(log);
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

    @Transactional
    public TestAnswerResultResponse submitTestAnswer(SubmitTestAnswerRequest request, Authentication authentication) {
        Card card = cardRepository.findById(request.cardId())
                .orElseThrow(() -> new ResourceNotFoundException("Card not found"));

        if (card.getCorrectOption() == null) {
            throw new IllegalStateException("This card has no test format defined");
        }

        boolean correct = card.getCorrectOption() == request.selectedOption();
        int rating = correct ? 3 : 1; // poprawna -> Good, błędna -> Again

        submitReview(new SubmitReviewRequest(request.cardId(), rating, request.timeSpentSeconds()), authentication);

        return new TestAnswerResultResponse(correct, card.getCorrectOption(), card.getExplanation());
    }
}