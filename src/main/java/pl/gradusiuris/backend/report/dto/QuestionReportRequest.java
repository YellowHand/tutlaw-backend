package pl.gradusiuris.backend.report.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pl.gradusiuris.backend.report.QuestionReportReason;

public record QuestionReportRequest(
        @NotNull(message = "wybierz powód zgłoszenia") QuestionReportReason reason,
        @Size(max = 4000, message = "komentarz jest zbyt długi") String comment
) {
}
