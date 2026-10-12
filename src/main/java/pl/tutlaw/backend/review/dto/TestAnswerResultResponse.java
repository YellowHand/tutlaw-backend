package pl.tutlaw.backend.review.dto;

import pl.tutlaw.backend.card.CorrectOption;

public record TestAnswerResultResponse(
        boolean correct,
        CorrectOption correctOption,
        String explanation
) {
}