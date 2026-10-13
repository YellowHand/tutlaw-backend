package pl.tutlaw.backend.review.dto;

import pl.tutlaw.backend.card.QuestionOrigin;

import java.time.LocalDate;

/**
 * Fiszka w kolejce. {@code lapses} i {@code leech} opisują stan sprzed tej sesji (dla nowej karty 0 i false):
 * na odwrocie fiszki pozwalają zaproponować zawieszenie pytania, z którym użytkownik od dawna sobie nie radzi.
 */
public record ReviewQueueItemResponse(
        Long cardId,
        String front,
        String back,
        Integer pageNumber,
        boolean verbatimQuote,
        String sourceName,
        String legalBasis,
        LocalDate validAsOf,
        int lapses,
        boolean leech,
        QuestionOrigin origin,
        Integer examYear
) {
}