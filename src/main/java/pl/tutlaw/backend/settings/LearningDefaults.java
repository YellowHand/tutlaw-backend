package pl.tutlaw.backend.settings;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Wartości domyślne nauki z konfiguracji serwera (application.yml, sekcja app.fsrs). */
@Component
public class LearningDefaults {

    private final int newFlashcardsPerDay;
    private final int newTestQuestionsPerDay;
    private final int maxReviewsPerDay;
    private final double retention;

    public LearningDefaults(
            @Value("${app.fsrs.new-flashcards-per-day}") int newFlashcardsPerDay,
            @Value("${app.fsrs.new-test-questions-per-day:40}") int newTestQuestionsPerDay,
            @Value("${app.fsrs.max-reviews-per-day:100}") int maxReviewsPerDay,
            @Value("${app.fsrs.request-retention}") double retention) {
        this.newFlashcardsPerDay = newFlashcardsPerDay;
        this.newTestQuestionsPerDay = newTestQuestionsPerDay;
        this.maxReviewsPerDay = maxReviewsPerDay;
        this.retention = retention;
    }

    public int newFlashcardsPerDay() {
        return newFlashcardsPerDay;
    }

    public int newTestQuestionsPerDay() {
        return newTestQuestionsPerDay;
    }

    public int maxReviewsPerDay() {
        return maxReviewsPerDay;
    }

    public double retention() {
        return retention;
    }
}
