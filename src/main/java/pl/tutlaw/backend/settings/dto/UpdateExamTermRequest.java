package pl.tutlaw.backend.settings.dto;

import java.time.LocalDate;

/** Nowy termin egzaminu aplikacji. {@code null} usuwa termin („termin nieogłoszony"). */
public record UpdateExamTermRequest(LocalDate examDate) {
}
