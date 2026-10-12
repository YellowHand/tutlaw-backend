package pl.tutlaw.backend.report.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pl.tutlaw.backend.report.CardReportReason;

public record CardReportRequest(
        @NotNull(message = "wybierz powód zgłoszenia") CardReportReason reason,
        @Size(max = 4000, message = "komentarz jest zbyt długi") String comment
) {
}
