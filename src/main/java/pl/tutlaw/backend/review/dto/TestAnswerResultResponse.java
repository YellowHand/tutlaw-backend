package pl.tutlaw.backend.review.dto;

import pl.tutlaw.backend.card.CorrectOption;

import java.time.LocalDate;

public record TestAnswerResultResponse(
        boolean correct,
        CorrectOption correctOption,
        String explanation,
        String legalBasis,
        LocalDate validAsOf
) {
}