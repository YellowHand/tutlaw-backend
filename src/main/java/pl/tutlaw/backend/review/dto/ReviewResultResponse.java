package pl.tutlaw.backend.review.dto;

/** Wynik oceny fiszki. {@code firstReview}: to była pierwsza ocena tej fiszki, czyli zaczęła się uczyć właśnie teraz. */
public record ReviewResultResponse(boolean firstReview) {
}
