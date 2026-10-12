package pl.tutlaw.backend.review.dto;

/**
 * Stan zaległości w wybranym zakresie nauki.
 *
 * @param dueNow     ile kart ma teraz minięty termin powtórki
 * @param deferred   ile z nich nie zmieści się dziś w sesji (limit dzienny) i poczeka na kolejne dni
 * @param dailyLimit dzienny limit zaległych powtórek
 * @param doneToday  ile zaległych powtórek zrobiono już dziś
 */
public record BacklogResponse(long dueNow, long deferred, int dailyLimit, long doneToday) {
}
