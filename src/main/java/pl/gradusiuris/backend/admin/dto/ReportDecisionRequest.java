package pl.gradusiuris.backend.admin.dto;

import jakarta.validation.constraints.NotNull;
import pl.gradusiuris.backend.report.QuestionReportStatus;

/** Nowy stan zgłoszenia: RESOLVED (poprawiono), REJECTED (odrzucono) albo NEW (otwórz ponownie). */
public record ReportDecisionRequest(@NotNull(message = "podaj stan zgłoszenia") QuestionReportStatus status) {
}
