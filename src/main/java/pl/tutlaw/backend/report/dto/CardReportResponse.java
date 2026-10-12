package pl.tutlaw.backend.report.dto;

/** {@code updated}: true, gdy użytkownik miał już otwarte zgłoszenie tego pytania i uzupełniliśmy je zamiast dodawać nowe. */
public record CardReportResponse(Long id, boolean updated) {
}
