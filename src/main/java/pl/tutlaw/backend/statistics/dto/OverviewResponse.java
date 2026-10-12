package pl.tutlaw.backend.statistics.dto;

import java.util.List;

public record OverviewResponse(
        int totalCards,
        int cardsStarted,
        int matureCards,
        /** Procent udanych powtórek z 30 dni; {@code null}, gdy nie było jeszcze żadnej prawdziwej powtórki. */
        Double retentionRate,
        List<DailyForecastItem> upcomingReviews
) {
}