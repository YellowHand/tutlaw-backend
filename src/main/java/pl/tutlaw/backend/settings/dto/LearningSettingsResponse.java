package pl.tutlaw.backend.settings.dto;

import java.time.LocalDate;

/**
 * Ustawienia nauki widoczne dla użytkownika: wartości obowiązujące (nigdy {@code null}), domyślne do podpowiedzi
 * i, gdy ustawiono datę egzaminu, plan nowych pytań na dzień.
 *
 * @param examPassed zapisana data egzaminu jest już w przeszłości (ustawienia egzaminacyjne wtedy nie działają)
 * @param plan       {@code null}, gdy nie ma daty egzaminu albo już minęła
 */
public record LearningSettingsResponse(
        int newCardsPerDay,
        int newTestQuestionsPerDay,
        int maxReviewsPerDay,
        double desiredRetention,
        LocalDate examDate,
        boolean shuffleOptions,
        Long daysToExam,
        boolean examPassed,
        double effectiveRetention,
        boolean retentionBoosted,
        Defaults defaults,
        Plan plan
) {

    public record Defaults(int newCardsPerDay, int newTestQuestionsPerDay, int maxReviewsPerDay, double desiredRetention) {
    }

    /**
     * @param planningDays                dni, w których warto jeszcze uczyć się nowego (do egzaminu minus rezerwa na same powtórki)
     * @param suggestedTestQuestionsPerDay ile nowych pytań testowych dziennie, żeby zdążyć je zacząć przed egzaminem
     * @param suggestedFlashcardsPerDay    to samo dla fiszek (kart bez opcji A/B/C)
     */
    public record Plan(
            long newTestQuestionsLeft,
            long newFlashcardsLeft,
            long planningDays,
            long suggestedTestQuestionsPerDay,
            long suggestedFlashcardsPerDay
    ) {
    }
}
