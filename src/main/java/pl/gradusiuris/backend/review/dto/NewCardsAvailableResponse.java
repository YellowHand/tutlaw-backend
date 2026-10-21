package pl.gradusiuris.backend.review.dto;

/** Ile nowych (jeszcze nietkniętych w danym trybie) pytań czeka w zakresie. */
public record NewCardsAvailableResponse(int availableCount) {
}
