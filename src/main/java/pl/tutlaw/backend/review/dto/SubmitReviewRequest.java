package pl.tutlaw.backend.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SubmitReviewRequest(
        @NotNull Long cardId,
        @NotNull @Min(1) @Max(4) Integer rating,
        Double timeSpentSeconds
) {
}