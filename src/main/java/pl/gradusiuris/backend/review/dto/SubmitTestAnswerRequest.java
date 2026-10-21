package pl.gradusiuris.backend.review.dto;

import jakarta.validation.constraints.NotNull;
import pl.gradusiuris.backend.question.CorrectOption;

/**
 * @param guessed opcjonalne; true, gdy użytkownik zaznaczył, że zgadywał.
 *                Poprawna, ale zgadywana odpowiedź liczy się jako „trudne" (wróci wcześniej),
 *                a nie jako „znam". Brak pola = zachowanie jak dotąd.
 * @param sessionId opcjonalne; numer zapisanego własnego testu, do którego należy ta odpowiedź (z odpowiedzi
 *                  na ułożenie testu). Odpowiedź trafia wtedy także do tego testu ("Moje testy").
 */
public record SubmitTestAnswerRequest(
        @NotNull Long questionId,
        @NotNull CorrectOption selectedOption,
        Double timeSpentSeconds,
        Boolean guessed,
        Long sessionId
) {
}
