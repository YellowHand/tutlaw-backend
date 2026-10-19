package pl.tutlaw.backend.review.dto;

/** Ile nowych (jeszcze nietkniętych w danym trybie) pytań czeka w zakresie. */
public record NewCardsAvailableResponse(int availableCount) {
}
