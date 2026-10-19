package pl.tutlaw.backend.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pl.tutlaw.backend.review.CustomTestKind;

import java.util.List;

/**
 * Własny test: co ma w nim być. Zakres to suma wskazanych dziedzin ({@code fieldIds}) i aktów prawnych
 * ({@code actIds}); bez żadnego z nich test obejmuje całą pulę użytkownika.
 *
 * @param kind          które pytania (domyślnie wszystkie)
 * @param questionCount ile pytań wylosować: 10, 20, 50, 100 albo 150. Gdy w zakresie jest mniej niż 10 pytań, jedyny
 *                      wybór to wszystkie z nich (liczba równa liczbie dostępnych). Gdy jest ich mniej niż wybrano,
 *                      test dostaje wszystkie
 */
public record CustomTestRequest(
        @Size(max = 200, message = "za dużo wybranych dziedzin")
        List<Long> fieldIds,
        @Size(max = 200, message = "za dużo wybranych aktów prawnych")
        List<Long> actIds,
        CustomTestKind kind,
        @NotNull(message = "liczba pytań jest wymagana")
        @Min(value = 1, message = "liczba pytań musi wynosić od 1 do 150")
        @Max(value = 150, message = "liczba pytań musi wynosić od 1 do 150")
        Integer questionCount
) {
}
