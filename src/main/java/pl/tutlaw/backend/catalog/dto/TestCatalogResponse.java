package pl.tutlaw.backend.catalog.dto;

import pl.tutlaw.backend.legal.LegalActKind;
import pl.tutlaw.backend.source.SourceKind;

import java.util.List;

/**
 * Drzewo do ekranu Testów: dziedzina → akt prawny, oraz osobno źródła (arkusze egzaminów i pytania autorskie),
 * z licznikami pytań dla zalogowanego użytkownika. Dziedziny są zawsze wszystkie, ale akty tylko te z puli użytkownika
 * (jego ścieżka i ewentualne zawężenie); liczą się pytania z wybranych źródeł (filtr źródeł).
 * <ul>
 *   <li>{@code questionCount}: pytania, które użytkownik może teraz rozwiązywać,</li>
 *   <li>{@code solvedCount}: ile z nich rozwiązał co najmniej raz w teście.</li>
 * </ul>
 *
 * @param narrowed czy pula jest zawężona przez użytkownika do wybranych aktów
 */
public record TestCatalogResponse(boolean narrowed, List<FieldNode> fields, List<SourceNode> sources) {

    public record FieldNode(
            Long id, String name, String slug,
            int questionCount, int solvedCount,
            List<ActNode> acts
    ) {
    }

    public record ActNode(
            Long id, String name, String shortName, String slug, LegalActKind kind,
            int questionCount, int solvedCount
    ) {
    }

    /**
     * Źródło pytań (arkusz albo pytania autorskie).
     *
     * @param group OWN_SHEETS (arkusze wybranej ścieżki), OTHER_SHEETS (arkusze innych ścieżek) albo AUTHORED
     */
    public record SourceNode(
            Long id, String name, SourceKind kind, String group, Integer examYear,
            int questionCount, int solvedCount
    ) {
    }
}
