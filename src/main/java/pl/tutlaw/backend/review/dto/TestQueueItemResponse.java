package pl.tutlaw.backend.review.dto;

import pl.tutlaw.backend.card.QuestionOrigin;

/** Pytanie testowe w kolejce. {@code origin} i {@code examYear} mówią, skąd pochodzi (autorskie albo egzamin z roku). */
public record TestQueueItemResponse(
        Long cardId,
        String front,
        String optionA,
        String optionB,
        String optionC,
        String sourceName,
        QuestionOrigin origin,
        Integer examYear
) {
}