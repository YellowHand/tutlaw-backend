package pl.tutlaw.backend.admin.dto;

import pl.tutlaw.backend.question.CorrectOption;
import pl.tutlaw.backend.report.QuestionReportReason;
import pl.tutlaw.backend.report.QuestionReportStatus;

import java.time.LocalDateTime;

/**
 * Zgłoszenie błędu w pytaniu razem z treścią pytania, żeby dało się ocenić je bez przechodzenia dalej. Zgłaszającego
 * nie ujawniamy (administrator ocenia treść, nie osobę). {@code openForQuestion} to liczba otwartych zgłoszeń tego pytania.
 */
public record AdminReportResponse(
        Long id,
        Long questionId,
        String questionBody,
        String optionA,
        String optionB,
        String optionC,
        CorrectOption correctOption,
        boolean questionHidden,
        QuestionReportReason reason,
        String comment,
        QuestionReportStatus status,
        LocalDateTime createdAt,
        LocalDateTime resolvedAt,
        long openForQuestion
) {
}
