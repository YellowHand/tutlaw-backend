package pl.gradusiuris.backend.report;

/** Stan zgłoszenia. Rozpatruje je administrator w panelu ({@code /api/admin/reports}). */
public enum QuestionReportStatus {
    NEW,
    RESOLVED,
    REJECTED
}
