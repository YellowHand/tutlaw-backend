package pl.tutlaw.backend.review.dto;

import pl.tutlaw.backend.card.CorrectOption;

import java.time.LocalDate;

/**
 * Wynik odpowiedzi w teście.
 *
 * @param lapses ile razy użytkownik pomylił się w tym pytaniu (łącznie z tą odpowiedzią)
 * @param leech  czy pytanie jest już „trudne" (liczba błędów osiągnęła próg), czyli warto zaproponować zawieszenie
 */
public record TestAnswerResultResponse(
        boolean correct,
        CorrectOption correctOption,
        String explanation,
        String legalBasis,
        LocalDate validAsOf,
        int lapses,
        boolean leech
) {
}