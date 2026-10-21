package pl.tutlaw.backend.exam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pl.tutlaw.backend.pool.SourceMode;
import pl.tutlaw.backend.question.CorrectOption;

import java.util.List;

/**
 * Zakończony egzamin. Serwer sam sprawdza odpowiedzi i sam ustala próg zaliczenia (format jest stały), a od klienta
 * bierze tylko to, czego nie może wiedzieć: wybrane odpowiedzi i czas.
 *
 * @param timeLimitMinutes null = egzamin bez limitu czasu
 * @param timedOut         czy egzamin zakończył się automatycznie z powodu końca czasu
 * @param source           źródło, z którego losowano pytania (tylko do podpisu w historii; brak = wszystkie)
 * @param sheetId          numer arkusza, gdy egzamin był egzaminem MS z wybranego roku (tylko do podpisu w historii,
 *                         np. „Egzamin MS: Notarialna 2024"); ma pierwszeństwo przed {@code source}
 */
public record ExamSubmitRequest(
        @Min(value = 1, message = "limit czasu musi wynosić od 1 do 600 minut")
        @Max(value = 600, message = "limit czasu musi wynosić od 1 do 600 minut")
        Integer timeLimitMinutes,
        @NotNull(message = "czas trwania jest wymagany")
        @Min(value = 0, message = "czas trwania nie może być ujemny")
        @Max(value = 172_800, message = "czas trwania jest zbyt długi")
        Integer durationSeconds,
        Boolean timedOut,
        @NotEmpty(message = "brak odpowiedzi do sprawdzenia")
        @Size(max = 150, message = "zbyt wiele pytań (egzamin ma najwyżej 150)")
        List<@Valid Answer> answers,
        SourceMode source,
        Long sheetId
) {

    /** @param selectedOption null = pytanie bez odpowiedzi */
    public record Answer(
            @NotNull(message = "brak numeru pytania") Long questionId,
            CorrectOption selectedOption
    ) {
    }
}
