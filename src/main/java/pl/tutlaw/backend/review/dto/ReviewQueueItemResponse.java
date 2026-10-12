package pl.tutlaw.backend.review.dto;

import java.time.LocalDate;

public record ReviewQueueItemResponse(
        Long cardId,
        String front,
        String back,
        Integer pageNumber,
        boolean verbatimQuote,
        String sourceName,
        String legalBasis,
        LocalDate validAsOf
) {
}