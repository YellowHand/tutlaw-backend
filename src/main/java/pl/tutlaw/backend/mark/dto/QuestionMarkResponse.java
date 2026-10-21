package pl.tutlaw.backend.mark.dto;

import pl.tutlaw.backend.mark.MarkOrigin;
import pl.tutlaw.backend.mark.MarkStatus;
import pl.tutlaw.backend.mark.QuestionMarkType;

/** Jedno oznaczenie pytania po decyzji administratora. */
public record QuestionMarkResponse(Long questionId, QuestionMarkType mark, MarkOrigin origin, MarkStatus status) {
}
