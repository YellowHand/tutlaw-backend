package pl.tutlaw.backend.pool.dto;

import pl.tutlaw.backend.legal.LegalActKind;
import pl.tutlaw.backend.track.dto.TrackSummary;

import java.util.List;

/**
 * Pula pytań użytkownika do okna „Pula pytań": ścieżka i zawężenie do aktów (student: do przedmiotów).
 *
 * @param track         wybrana ścieżka ({@code null}, gdy jeszcze nie wybrano)
 * @param narrowed      czy użytkownik zawęził pulę do wybranych aktów albo, na ścieżce Student, do wybranych przedmiotów
 *                      (bez zawężenia obowiązują wszystkie akty ścieżki albo wszystkie przedmioty)
 * @param questionCount pytania w puli po zawężeniu (ze wszystkich źródeł)
 * @param fields        dziedziny z aktami ścieżki do zaznaczenia; dziedziny bez aktów w puli użytkownika są pomijane
 * @param courses       przedmioty do wyboru na ścieżce Student (na pozostałych ścieżkach pusta lista)
 */
public record PoolResponse(
        TrackSummary track,
        boolean narrowed,
        int questionCount,
        List<FieldNode> fields,
        List<CourseNode> courses
) {

    /**
     * Przedmiot studenta do zaznaczenia; {@code questionCount} to pytania jego aktów ze wszystkich źródeł, a
     * {@code selected} mówi, czy przedmiot jest w puli (bez własnego wyboru studenta w puli są wszystkie przedmioty).
     */
    public record CourseNode(Long id, String name, String slug, int questionCount, boolean selected) {
    }

    public record FieldNode(Long id, String name, String slug, List<ActNode> acts) {
    }

    /** Akt do zaznaczenia; {@code questionCount} to pytania aktu ze wszystkich źródeł. */
    public record ActNode(
            Long id, String name, String shortName, LegalActKind kind, int questionCount, boolean selected) {
    }
}
