package pl.tutlaw.backend.admin;

import pl.tutlaw.backend.question.CorrectOption;

import java.time.LocalDate;

/** Treść pytania po sprawdzeniu i oczyszczeniu (bez białych znaków na brzegach, puste pola opcjonalne jako null). */
record QuestionDraft(
        String body,
        String optionA,
        String optionB,
        String optionC,
        CorrectOption correctOption,
        String explanation,
        String legalBasis,
        LocalDate validAsOf,
        LocalDate lawChangedAt,
        String lawChangeNote
) {
}
