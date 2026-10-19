package pl.tutlaw.backend.review.dto;

import pl.tutlaw.backend.source.SourceKind;

/**
 * Pytanie testowe w kolejce. {@code sourceName}, {@code sourceKind} i {@code examYear} mówią, skąd pochodzi (arkusz
 * egzaminu z roku albo pytanie autorskie), a {@code actName} do jakiego aktu prawnego należy.
 */
public record TestQueueItemResponse(
        Long questionId,
        String body,
        String optionA,
        String optionB,
        String optionC,
        String actName,
        String actShortName,
        String sourceName,
        SourceKind sourceKind,
        Integer examYear
) {
}
