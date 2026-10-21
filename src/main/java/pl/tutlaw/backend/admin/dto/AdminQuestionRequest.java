package pl.tutlaw.backend.admin.dto;

import jakarta.validation.constraints.NotNull;
import pl.tutlaw.backend.question.CorrectOption;

import java.time.LocalDate;

/**
 * Pytanie dodawane albo zmieniane w panelu. Akt i źródło (arkusz albo pytania autorskie) są wymagane, rozdział
 * opcjonalny i musi należeć do wybranego aktu. Treść sprawdzają reguły ({@code QuestionRules}), więc błędy mają polskie opisy.
 */
public record AdminQuestionRequest(
        @NotNull(message = "wybierz akt prawny") Long actId,
        Long sectionId,
        @NotNull(message = "wybierz źródło pytania") Long sourceId,
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
