package pl.tutlaw.backend.exam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pl.tutlaw.backend.card.CorrectOption;

import java.util.List;

/**
 * Zakończony egzamin. Serwer sam sprawdza odpowiedzi, a od klienta bierze tylko to, czego nie może wiedzieć:
 * wybrane odpowiedzi, ustawienia egzaminu i czas.
 *
 * @param timeLimitMinutes null = egzamin bez limitu czasu
 * @param timedOut         czy egzamin zakończył się automatycznie z powodu końca czasu
 */
public record ExamSubmitRequest(
        Long categoryId,
        Long subjectId,
        Long sourceId,
        @Min(value = 1, message = "limit czasu musi wynosić od 1 do 600 minut")
        @Max(value = 600, message = "limit czasu musi wynosić od 1 do 600 minut")
        Integer timeLimitMinutes,
        @NotNull(message = "próg zaliczenia jest wymagany")
        @Min(value = 1, message = "próg zaliczenia musi wynosić od 1 do 100 procent")
        @Max(value = 100, message = "próg zaliczenia musi wynosić od 1 do 100 procent")
        Integer passThresholdPercent,
        @NotNull(message = "czas trwania jest wymagany")
        @Min(value = 0, message = "czas trwania nie może być ujemny")
        @Max(value = 172_800, message = "czas trwania jest zbyt długi")
        Integer durationSeconds,
        Boolean timedOut,
        @NotEmpty(message = "brak odpowiedzi do sprawdzenia")
        @Size(max = 200, message = "zbyt wiele pytań (maksymalnie 200)")
        List<@Valid Answer> answers
) {

    /** @param selectedOption null = pytanie bez odpowiedzi */
    public record Answer(
            @NotNull(message = "brak numeru pytania") Long cardId,
            CorrectOption selectedOption
    ) {
    }
}
