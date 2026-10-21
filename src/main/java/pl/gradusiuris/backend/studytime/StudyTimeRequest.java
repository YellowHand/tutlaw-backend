package pl.gradusiuris.backend.studytime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** @param seconds ile sekund nauki doliczyć do dzisiejszego dnia (1-300) */
public record StudyTimeRequest(
        @NotNull(message = "podaj liczbę sekund")
        @Min(value = 1, message = "liczba sekund musi wynosić od 1 do 300")
        @Max(value = 300, message = "liczba sekund musi wynosić od 1 do 300")
        Integer seconds
) {
}
