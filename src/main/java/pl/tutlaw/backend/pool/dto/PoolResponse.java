package pl.tutlaw.backend.pool.dto;

import pl.tutlaw.backend.legal.LegalActKind;
import pl.tutlaw.backend.track.dto.TrackSummary;

import java.util.List;

/**
 * Pula pytań użytkownika do okna „Pula pytań": ścieżka, zawężenie do aktów (student: do przedmiotów) i filtr źródeł.
 *
 * @param track         wybrana ścieżka ({@code null}, gdy jeszcze nie wybrano)
 * @param narrowed      czy użytkownik zawęził pulę do wybranych aktów albo, na ścieżce Student, do wybranych przedmiotów
 *                      (bez zawężenia obowiązują wszystkie akty ścieżki albo wszystkie przedmioty)
 * @param questionCount pytania w puli po zawężeniu i filtrze źródeł
 * @param fields        dziedziny z aktami ścieżki do zaznaczenia; dziedziny bez aktów w puli użytkownika są pomijane
 * @param courses       przedmioty do wyboru na ścieżce Student (na pozostałych ścieżkach pusta lista)
 * @param sourceGroups  trzy grupy źródeł (własne arkusze, arkusze innych ścieżek, pytania autorskie)
 */
public record PoolResponse(
        TrackSummary track,
        boolean narrowed,
        int questionCount,
        List<FieldNode> fields,
        List<CourseNode> courses,
        List<SourceGroupNode> sourceGroups
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

    /**
     * @param code          OWN_SHEETS, OTHER_SHEETS albo AUTHORED
     * @param selected      czy grupa jest włączona w filtrze
     * @param questionCount pytania tej grupy w aktach puli (przed filtrem źródeł); 0 oznacza, że grupa nic nie da
     */
    public record SourceGroupNode(String code, boolean selected, int questionCount) {
    }
}
