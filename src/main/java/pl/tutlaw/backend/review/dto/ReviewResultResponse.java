package pl.tutlaw.backend.review.dto;

/** Wynik oceny fiszki. {@code newCard}: to była pierwsza ocena tej fiszki, czyli zaczęła się uczyć właśnie teraz. */
public record ReviewResultResponse(boolean newCard) {
}
