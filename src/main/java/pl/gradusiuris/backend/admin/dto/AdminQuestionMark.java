package pl.gradusiuris.backend.admin.dto;

import pl.gradusiuris.backend.mark.MarkStatus;
import pl.gradusiuris.backend.mark.QuestionMarkType;

/** Oznaczenie pytania (rodzaj i stan) w widoku pytania w panelu. */
public record AdminQuestionMark(QuestionMarkType mark, MarkStatus status) {
}
