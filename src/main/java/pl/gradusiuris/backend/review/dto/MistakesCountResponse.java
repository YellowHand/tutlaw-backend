package pl.gradusiuris.backend.review.dto;

/**
 * Ile pytań użytkownik może teraz powtórzyć w "Powtórz moje błędy".
 *
 * @param count        pytania, w których ostatnia odpowiedź w teście była błędna (w okresie {@code lookbackDays}),
 *                     nadal dostępne i niezawieszone
 * @param lookbackDays z ilu ostatnich dni liczą się błędy
 * @param sessionSize  ile pytań maksymalnie wchodzi do jednej sesji
 */
public record MistakesCountResponse(int count, int lookbackDays, int sessionSize) {
}
