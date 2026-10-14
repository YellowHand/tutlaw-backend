package pl.tutlaw.backend.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pl.tutlaw.backend.review.CustomTestKind;

import java.util.List;

/**
 * Własny test: co ma w nim być. Zakres to suma wskazanych rodzajów ({@code categoryIds}) i aktów prawnych albo
 * przedmiotów ({@code subjectIds}); bez żadnego z nich test obejmuje cały dostępny materiał. Zakres lat egzaminu
 * jest opcjonalny: gdy podany, pytania bez roku (autorskie) nie wchodzą do testu.
 *
 * @param kind          które pytania (domyślnie wszystkie)
 * @param yearFrom      najwcześniejszy rok egzaminu (włącznie) albo null
 * @param yearTo        najpóźniejszy rok egzaminu (włącznie) albo null
 * @param questionCount ile pytań wylosować (1-200); gdy jest ich mniej, test dostaje wszystkie
 */
public record CustomTestRequest(
        @Size(max = 200, message = "za dużo wybranych rodzajów")
        List<Long> categoryIds,
        @Size(max = 200, message = "za dużo wybranych aktów prawnych")
        List<Long> subjectIds,
        CustomTestKind kind,
        Integer yearFrom,
        Integer yearTo,
        @NotNull(message = "liczba pytań jest wymagana")
        @Min(value = 1, message = "liczba pytań musi wynosić od 1 do 200")
        @Max(value = 200, message = "liczba pytań musi wynosić od 1 do 200")
        Integer questionCount
) {
}
