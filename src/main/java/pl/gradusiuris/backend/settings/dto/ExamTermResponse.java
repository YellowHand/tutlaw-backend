package pl.gradusiuris.backend.settings.dto;

import pl.gradusiuris.backend.track.dto.TrackSummary;

import java.time.LocalDate;

/**
 * Termin egzaminu jednej aplikacji.
 *
 * @param examDate   data egzaminu albo {@code null} (termin nieogłoszony)
 * @param daysToExam ile dni do egzaminu; {@code null}, gdy daty nie ma albo egzamin już się odbył
 */
public record ExamTermResponse(TrackSummary track, LocalDate examDate, Long daysToExam) {
}
