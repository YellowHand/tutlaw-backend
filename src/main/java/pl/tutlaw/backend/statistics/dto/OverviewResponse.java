package pl.tutlaw.backend.statistics.dto;

import java.util.List;

/**
 * Przegląd nauki w wybranym zakresie i trybie.
 *
 * @param totalQuestions   ile pytań (w trybie fiszek: odblokowanych fiszek) użytkownik może teraz ćwiczyć
 * @param questionsStarted ile z nich zaczął
 * @param matureQuestions  ile jest utrwalonych (stabilność powyżej progu)
 * @param retentionRate    procent udanych powtórek z 30 dni; {@code null}, gdy nie było jeszcze żadnej prawdziwej powtórki
 */
public record OverviewResponse(
        int totalQuestions,
        int questionsStarted,
        int matureQuestions,
        Double retentionRate,
        List<DailyForecastItem> upcomingReviews
) {
}
