package pl.tutlaw.backend.review.dto;

public record ReviewQueueItemResponse(
        Long cardId,
        String front,
        String back,
        Integer pageNumber,
        boolean verbatimQuote,
        String sourceName
) {
}