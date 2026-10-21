package pl.gradusiuris.backend.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Ocena fiszki pytania (1–4). */
public record SubmitReviewRequest(
        @NotNull Long questionId,
        @NotNull @Min(1) @Max(4) Integer rating,
        Double timeSpentSeconds
) {
}
