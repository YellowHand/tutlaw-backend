package pl.tutlaw.backend.admin.dto;

import pl.tutlaw.backend.track.TrackKind;

import java.time.LocalDate;
import java.util.List;

/**
 * Aplikacja (albo ścieżka Student) w panelu administratora.
 *
 * @param actIds     numery aktów aplikacji (także ukrytych); dla Studenta suma aktów wszystkich przedmiotów (tylko do odczytu)
 * @param sheetCount ile arkuszy ma aplikacja (także ukrytych)
 * @param userCount  ilu użytkowników wybrało tę ścieżkę
 * @param examDate   termin egzaminu albo {@code null} (nieogłoszony; Student nie ma terminu)
 */
public record AdminTrackResponse(
        Long id,
        String slug,
        String name,
        String shortName,
        TrackKind kind,
        int sortOrder,
        List<Long> actIds,
        int sheetCount,
        long userCount,
        LocalDate examDate
) {
}
