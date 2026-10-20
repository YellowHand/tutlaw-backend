package pl.tutlaw.backend.catalog.dto;

import pl.tutlaw.backend.legal.LegalActKind;
import pl.tutlaw.backend.source.SourceKind;

import java.util.List;

/**
 * Drzewo do ekranu Testów: dziedzina → akt prawny, oraz osobno źródła (arkusze egzaminów i pytania autorskie),
 * z licznikami pytań dla zalogowanego użytkownika. Dziedziny bez aktów w puli użytkownika są pomijane, a akty to tylko
 * te z jego puli (jego ścieżka i ewentualne zawężenie); liczą się pytania z wybranych źródeł (filtr źródeł). Student
 * dostaje dodatkowo listę swoich przedmiotów ({@code courses}).
 * <ul>
 *   <li>{@code questionCount}: pytania, które użytkownik może teraz rozwiązywać,</li>
 *   <li>{@code solvedCount}: ile z nich rozwiązał co najmniej raz w teście.</li>
 * </ul>
 *
 * @param narrowed czy pula jest zawężona przez użytkownika do wybranych aktów (student: do wybranych przedmiotów)
 * @param courses  przedmioty w puli studenta (na pozostałych ścieżkach pusta lista)
 */
public record TestCatalogResponse(
        boolean narrowed, List<FieldNode> fields, List<CourseNode> courses, List<SourceNode> sources) {

    /**
     * Przedmiot studenta z licznikami pytań z jego aktów (z dozwolonych źródeł). {@code actIds} to jego akty,
     * także te, które mają zero pytań.
     */
    public record CourseNode(Long id, String name, String slug, int questionCount, int solvedCount, List<Long> actIds) {
    }

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
