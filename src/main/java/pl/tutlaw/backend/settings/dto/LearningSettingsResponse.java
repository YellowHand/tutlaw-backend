package pl.tutlaw.backend.settings.dto;

import pl.tutlaw.backend.card.QuestionOrigin;
import pl.tutlaw.backend.settings.Theme;

import java.time.LocalDate;

/**
 * Ustawienia nauki widoczne dla użytkownika: wartości obowiązujące (nigdy {@code null}, poza wyborem egzaminu),
 * domyślne do podpowiedzi i, gdy znany jest termin egzaminu, plan nowych pytań na dzień.
 *
 * @param newTestQuestionsPerDay limit nowych pytań testowych na dzień, który naprawdę obowiązuje; przy limicie
 *                           automatycznym jest wyliczony z planu do egzaminu (nie mniej niż domyślny, nie więcej niż sufit)
 * @param newTestQuestionsAuto {@code true}, gdy użytkownik nie ustawił własnego limitu i obowiązuje limit automatyczny
 * @param theme      wygląd aplikacji wybrany przez użytkownika (domyślnie ciemny)
 * @param examOrigin aplikacja wybrana przez użytkownika; {@code null}, gdy jeszcze nie wybrał
 * @param examDate   termin egzaminu tej aplikacji (ustawia go administrator); tylko do odczytu, {@code null}, gdy
 *                   aplikacji nie wybrano albo termin nieogłoszony
 * @param examPassed termin egzaminu wybranej aplikacji jest już w przeszłości (ustawienia egzaminacyjne wtedy nie działają)
 * @param plan       {@code null}, gdy nie ma terminu egzaminu albo już minął
 */
public record LearningSettingsResponse(
        int newCardsPerDay,
        int newTestQuestionsPerDay,
        boolean newTestQuestionsAuto,
        int maxReviewsPerDay,
        double desiredRetention,
        QuestionOrigin examOrigin,
        LocalDate examDate,
        boolean shuffleOptions,
        Theme theme,
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
     * @param planningDays                dni, w których warto jeszcze uczyć się nowego (do egzaminu minus rezerwa na same
     *                                    powtórki, 14 dni); {@code 0} w ostatnich dwóch tygodniach: nowego się już nie planuje
     * @param requiredTestQuestionsPerDay  ile nowych pytań testowych dziennie trzeba, żeby zdążyć je zacząć przed rezerwą
     *                                    na powtórki (bez żadnego sufitu). Gdy większe niż {@code maxTestQuestionsPerDay},
     *                                    wszystkich nowych pytań nie da się zacząć na czas
     * @param maxTestQuestionsPerDay       sufit automatycznego limitu (powyżej powtórki rosną szybciej, niż da się je robić)
     * @param suggestedTestQuestionsPerDay cel na dziś: {@code requiredTestQuestionsPerDay}, ale nie więcej niż sufit.
     *                                    Liczony od stanu z początku dnia (nie spada w ciągu dnia),
     *                                    {@code 0} w ostatnich dwóch tygodniach
     * @param suggestedFlashcardsPerDay    to samo dla fiszek (kart bez opcji A/B/C)
     * @param newTestQuestionsToday       ile nowych pytań testowych użytkownik zaczął dzisiaj (do porównania z celem dnia)
     */
    public record Plan(
            long newTestQuestionsLeft,
            long newFlashcardsLeft,
            long planningDays,
            long requiredTestQuestionsPerDay,
            long maxTestQuestionsPerDay,
            long suggestedTestQuestionsPerDay,
            long suggestedFlashcardsPerDay,
            long newTestQuestionsToday
    ) {
    }
}
