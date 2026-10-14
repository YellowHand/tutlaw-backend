package pl.tutlaw.backend.settings;

import pl.tutlaw.backend.card.QuestionOrigin;

import java.time.LocalDate;

/**
 * Ustawienia nauki po rozstrzygnięciu wartości domyślnych i trybu egzaminacyjnego.
 *
 * @param newTestQuestionsPerDay limit nowych pytań testowych na dzień zapisany przez użytkownika; gdy
 *                           {@code newTestQuestionsAuto}, to wartość domyślna (dolna granica limitu automatycznego)
 * @param newTestQuestionsAuto użytkownik nie ustawił własnego limitu, więc obowiązuje limit automatyczny, który idzie
 *                           za planem do egzaminu (liczy go {@link LearningSettingsService#dailyNewTestQuestionLimit})
 * @param desiredRetention   retencja wybrana przez użytkownika (albo domyślna)
 * @param effectiveRetention retencja faktycznie użyta do liczenia terminów (w ostatnich dniach przed egzaminem wyższa)
 * @param examOrigin         aplikacja wybrana przez użytkownika ({@code null}, gdy jeszcze nie wybrał)
 * @param examDate           termin egzaminu tej aplikacji ustawiony przez administratora (także gdy już minął);
 *                           {@code null}, gdy aplikacji nie wybrano albo termin nieogłoszony
 * @param daysToExam         ile dni do egzaminu; {@code null}, gdy daty nie ma albo egzamin już się odbył
 */
public record EffectiveSettings(
        int newCardsPerDay,
        int newTestQuestionsPerDay,
        int maxReviewsPerDay,
        double desiredRetention,
        double effectiveRetention,
        QuestionOrigin examOrigin,
        LocalDate examDate,
        Long daysToExam,
        boolean shuffleOptions,
        boolean newTestQuestionsAuto
) {

    public boolean retentionBoosted() {
        return effectiveRetention > desiredRetention;
    }

    public boolean examPassed() {
        return examDate != null && daysToExam == null;
    }
}
