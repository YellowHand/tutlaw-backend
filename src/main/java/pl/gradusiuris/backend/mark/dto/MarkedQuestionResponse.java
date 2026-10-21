package pl.gradusiuris.backend.mark.dto;

import pl.gradusiuris.backend.mark.MarkOrigin;
import pl.gradusiuris.backend.mark.MarkStatus;
import pl.gradusiuris.backend.mark.QuestionMarkType;
import pl.gradusiuris.backend.question.CorrectOption;

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
