package pl.tutlaw.backend.settings;

import java.time.LocalDate;

/**
 * Ustawienia nauki po rozstrzygnięciu wartości domyślnych i trybu egzaminacyjnego.
 *
 * @param desiredRetention   retencja wybrana przez użytkownika (albo domyślna)
 * @param effectiveRetention retencja faktycznie użyta do liczenia terminów (w ostatnich dniach przed egzaminem wyższa)
 * @param examDate           zapisana data egzaminu (także gdy już minęła)
 * @param daysToExam         ile dni do egzaminu; {@code null}, gdy daty nie ma albo egzamin już się odbył
 */
public record EffectiveSettings(
        int newCardsPerDay,
        int newTestQuestionsPerDay,
        int maxReviewsPerDay,
        double desiredRetention,
        double effectiveRetention,
        LocalDate examDate,
        Long daysToExam,
        boolean shuffleOptions
) {

    public boolean retentionBoosted() {
        return effectiveRetention > desiredRetention;
    }

    public boolean examPassed() {
        return examDate != null && daysToExam == null;
    }
}
