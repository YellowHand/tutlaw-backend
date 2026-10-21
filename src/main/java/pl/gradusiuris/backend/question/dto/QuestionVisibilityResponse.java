package pl.gradusiuris.backend.question.dto;

/** Widoczność pytania po zmianie: {@code hidden} to ukryte całe pytanie, {@code flashcardHidden} tylko jego fiszka. */
public record QuestionVisibilityResponse(Long id, boolean hidden, boolean flashcardHidden) {
}
