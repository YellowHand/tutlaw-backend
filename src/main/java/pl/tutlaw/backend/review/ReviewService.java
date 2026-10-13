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

    private final UserCardStateRepository stateRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final CardRepository cardRepository;
    private final CurrentUserResolver currentUserResolver;
    private final FsrsEngine fsrsEngine;
    private final LearningSettingsService settingsService;
    private final IntervalFuzzer intervalFuzzer;
    private final LeechPolicy leechPolicy;
    private final Clock clock;

    /** Ocena fiszki: 1–4 wybrana przez użytkownika. „Nie pamiętam" (1) liczy się jako błąd. */
    @Transactional
    public void submitReview(SubmitReviewRequest request, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        Card card = findReadableCard(request.cardId(), user);

        // Fiszki i Testy to osobne światy: pytanie testowe ocenia się tylko w teście (zob. submitTestAnswer).
        if (card.getCorrectOption() != null) {
            throw new IllegalArgumentException("To pytanie jest dostępne tylko w Testach");
        }

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
        Card card = findReadableCard(request.cardId(), user);

        if (card.getCorrectOption() == null) {
            throw new IllegalArgumentException("To pytanie nie ma formatu testowego (brak poprawnej odpowiedzi)");
        }

        boolean correct = card.getCorrectOption() == request.selectedOption();
        boolean guessed = Boolean.TRUE.equals(request.guessed());

        // Pewny błąd (zły wybór bez „Nie jestem pewien") odblokowuje powiązaną fiszkę w Fiszkach. Informujemy o tym
        // tylko raz: gdy to pierwszy taki błąd w tym pytaniu i jest do niego jakaś fiszka.
        boolean unlocksFlashcard = !correct && !guessed
                && reviewLogRepository.countConfidentMistakes(user.getId(), card.getId()) == 0
                && cardRepository.existsByLinkedTestCardId(card.getId());

        UserCardState state = applyReview(user, card, ReviewPolicy.testRating(correct, guessed),
                request.timeSpentSeconds(), ReviewMode.TEST, correct, guessed, !correct);

        return new TestAnswerResultResponse(
                correct, card.getCorrectOption(), card.getExplanation(), card.getLegalBasis(), card.getValidAsOf(),
                state.getLapses(), leechPolicy.isLeech(state.getLapses()), unlocksFlashcard);
    }

    /**
     * Karta musi istnieć i być dostępna dla użytkownika (pula płaska albo przeczytany rozdział podręcznika).
     * Bez tego wystarczyło znać numer karty, żeby "odpowiadać" na pytania z nieodblokowanych rozdziałów.
     * Dla karty niedostępnej odpowiadamy tak samo jak dla nieistniejącej.
     */
    private Card findReadableCard(Long cardId, User user) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Card not found"));
        if (cardRepository.countReadableById(card.getId(), user.getId()) == 0) {
            throw new ResourceNotFoundException("Card not found");
        }
        return card;
    }

    /** Zapisuje odpowiedź i zwraca stan karty po niej (przy zdublowanym żądaniu: stan bez zmian). */
    private UserCardState applyReview(
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
        return state;
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
