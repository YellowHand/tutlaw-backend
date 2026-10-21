package pl.gradusiuris.backend.admin.dto;

/** Ile zgłoszeń jest w każdym stanie. */
public record AdminReportSummaryResponse(long open, long resolved, long rejected) {
}
