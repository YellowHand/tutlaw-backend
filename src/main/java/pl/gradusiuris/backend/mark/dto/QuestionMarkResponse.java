package pl.gradusiuris.backend.mark.dto;

import pl.gradusiuris.backend.mark.MarkOrigin;
import pl.gradusiuris.backend.mark.MarkStatus;
import pl.gradusiuris.backend.mark.QuestionMarkType;

/** Jedno oznaczenie pytania po decyzji administratora. */
public record QuestionMarkResponse(Long questionId, QuestionMarkType mark, MarkOrigin origin, MarkStatus status) {
}
