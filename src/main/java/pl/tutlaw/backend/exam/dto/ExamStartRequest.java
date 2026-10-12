package pl.tutlaw.backend.exam.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Zakres od najwęższego: źródło, przedmiot, kategoria; bez żadnego z nich egzamin obejmuje cały dostępny materiał.
 */
public record ExamStartRequest(
        Long categoryId,
        Long subjectId,
        Long sourceId,
        @NotNull(message = "liczba pytań jest wymagana")
        @Min(value = 5, message = "liczba pytań musi wynosić od 5 do 200")
        @Max(value = 200, message = "liczba pytań musi wynosić od 5 do 200")
        Integer questionCount
) {
}
