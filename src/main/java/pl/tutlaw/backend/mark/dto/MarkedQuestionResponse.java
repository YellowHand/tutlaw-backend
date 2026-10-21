package pl.tutlaw.backend.mark.dto;

import pl.tutlaw.backend.mark.MarkOrigin;
import pl.tutlaw.backend.mark.MarkStatus;
import pl.tutlaw.backend.mark.QuestionMarkType;
import pl.tutlaw.backend.question.CorrectOption;

/** Oznaczenie wraz z pytaniem, do przejrzenia (kolejka zatwierdzania). */
public record MarkedQuestionResponse(
        Long id,
        Long questionId,
        QuestionMarkType mark,
        MarkOrigin origin,
        MarkStatus status,
        String body,
        String optionA,
        String optionB,
        String optionC,
        CorrectOption correctOption,
        String sourceName,
        String actShortName
) {
}
