package pl.tutlaw.backend.review.dto;

public record TestQueueItemResponse(
        Long cardId,
        String front,
        String optionA,
        String optionB,
        String optionC,
        String sourceName
) {
}