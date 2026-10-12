package pl.tutlaw.backend.statistics.dto;

import java.time.LocalDateTime;

public record WeakCardResponse(
        Long cardId,
        String front,
        int lapses,
        double currentRetrievability,
        LocalDateTime lastReviewAt
) {
}