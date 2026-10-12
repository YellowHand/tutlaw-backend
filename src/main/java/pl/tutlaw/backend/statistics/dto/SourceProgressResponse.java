package pl.tutlaw.backend.statistics.dto;

import pl.tutlaw.backend.source.SourceType;

public record SourceProgressResponse(
        Long sourceId, String name, SourceType sourceType,
        int totalCards, int cardsStarted, double progressPercentage
) {
}