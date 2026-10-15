package pl.tutlaw.backend.review.dto;

import java.util.List;

/**
 * Wylosowane pytania własnego testu.
 *
 * @param cardIds   numery pytań w kolejności sesji (po wymieszaniu); sesję otwiera się przez kolejkę ćwiczeń
 * @param available ile pytań spełniało warunki (z nich losowane było {@code cardIds.size()})
 * @param requested o ile pytań prosił użytkownik
 * @param sessionId numer zapisanego testu ("Moje testy"); odpowiedzi w sesji podaje się z tym numerem
 */
public record CustomTestSelectionResponse(List<Long> cardIds, int available, int requested, Long sessionId) {
}
