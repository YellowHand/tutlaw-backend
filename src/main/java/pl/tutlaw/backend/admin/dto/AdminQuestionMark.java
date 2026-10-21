package pl.tutlaw.backend.admin.dto;

import pl.tutlaw.backend.mark.MarkStatus;
import pl.tutlaw.backend.mark.QuestionMarkType;

/** Oznaczenie pytania (rodzaj i stan) w widoku pytania w panelu. */
public record AdminQuestionMark(QuestionMarkType mark, MarkStatus status) {
}
