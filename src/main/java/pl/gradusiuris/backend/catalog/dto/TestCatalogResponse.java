package pl.gradusiuris.backend.catalog.dto;

import pl.gradusiuris.backend.legal.LegalActKind;

import java.util.List;

/**
 * Drzewo do kreatora Testów: dziedzina → akt prawny → rozdział, z licznikami pytań dla zalogowanego użytkownika.
 * Dziedziny bez aktów w puli użytkownika są pomijane, a akty to tylko te z jego puli (jego ścieżka i ewentualne
 * zawężenie); liczą się pytania z wybranego źródła ({@code SourceMode}: wszystkie, Ministerstwo, autorskie). Student
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
        boolean narrowed, List<FieldNode> fields, List<CourseNode> courses) {

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

    /**
     * @param sections rozdziały aktu, w których jest co rozwiązywać (puste, gdy akt nie ma rozdziałów). Pytania bez
     *                 rozdziału są tylko w akcie jako całości, więc suma rozdziałów może być mniejsza niż
     *                 {@code questionCount}
     */
    public record ActNode(
            Long id, String name, String shortName, String slug, LegalActKind kind,
            int questionCount, int solvedCount,
            List<SectionNode> sections
    ) {
    }

    /** Rozdział aktu z liczbą pytań (z wybranego źródła). */
    public record SectionNode(Long id, String name, int questionCount) {
    }
}
