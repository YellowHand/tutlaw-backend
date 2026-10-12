package pl.tutlaw.backend.statistics.dto;

import java.util.List;

public record OverviewResponse(
        int totalCards,
        int cardsStarted,
        int matureCards,
        double retentionRate,
        List<DailyForecastItem> upcomingReviews
) {
}