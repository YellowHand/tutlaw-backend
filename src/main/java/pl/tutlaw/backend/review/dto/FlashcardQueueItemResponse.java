package pl.tutlaw.backend.review.dto;

import pl.tutlaw.backend.source.SourceKind;

import java.time.LocalDate;

/**
 * Fiszka w kolejce: ta sama treść co pytanie testowe, w trybie fiszki. Przód to treść pytania, tył to poprawna
 * odpowiedź (tekst opcji) z wyjaśnieniem i podstawą prawną. {@code lapses} i {@code leech} opisują stan sprzed tej
 * sesji (dla nowej fiszki 0 i false): na odwrocie pozwalają zaproponować zawieszenie pytania, z którym użytkownik
 * od dawna sobie nie radzi.
 */
public record FlashcardQueueItemResponse(
        Long questionId,
        String front,
        String back,
        String explanation,
        String legalBasis,
        LocalDate validAsOf,
        String actName,
        String actShortName,
        String sourceName,
        SourceKind sourceKind,
        Integer examYear,
        int lapses,
        boolean leech
) {
}
