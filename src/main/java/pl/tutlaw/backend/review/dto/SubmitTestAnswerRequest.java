package pl.tutlaw.backend.review.dto;

import jakarta.validation.constraints.NotNull;
import pl.tutlaw.backend.card.CorrectOption;

public record SubmitTestAnswerRequest(
        @NotNull Long cardId,
        @NotNull CorrectOption selectedOption,
        Double timeSpentSeconds
) {
}
