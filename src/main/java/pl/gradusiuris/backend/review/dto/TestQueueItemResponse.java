package pl.gradusiuris.backend.review.dto;

import pl.gradusiuris.backend.question.dto.LawChangeInfo;
import pl.gradusiuris.backend.source.SourceKind;

/**
 * Pytanie testowe w kolejce. {@code sourceName}, {@code sourceKind} i {@code examYear} mówią, skąd pochodzi (arkusz
 * egzaminu z roku albo pytanie autorskie), a {@code actName} do jakiego aktu prawnego należy. {@code lawChange} to zmiana
 * w prawie dotykająca pytania ({@code null}, gdy jej nie ma).
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
        Integer examYear,
        LawChangeInfo lawChange
) {
}
