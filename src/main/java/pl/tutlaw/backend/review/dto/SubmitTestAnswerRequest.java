package pl.tutlaw.backend.review.dto;

import jakarta.validation.constraints.NotNull;
import pl.tutlaw.backend.card.CorrectOption;

/**
 * @param guessed opcjonalne; true, gdy użytkownik zaznaczył, że zgadywał.
 *                Poprawna, ale zgadywana odpowiedź liczy się jako „trudne" (wróci wcześniej),
 *                a nie jako „znam". Brak pola = zachowanie jak dotąd.
 */
public record SubmitTestAnswerRequest(
        @NotNull Long cardId,
        @NotNull CorrectOption selectedOption,
        Double timeSpentSeconds,
        Boolean guessed
) {
}
