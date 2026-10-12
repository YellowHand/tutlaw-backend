package pl.tutlaw.backend.catalog.dto;

import pl.tutlaw.backend.source.SourceType;

import java.util.List;

/**
 * Drzewo do ekranu Testów: rodzaj (kategoria) → przedmiot / akt prawny → źródło,
 * z licznikami pytań testowych (kart z trzema odpowiedziami) dla zalogowanego użytkownika.
 * <ul>
 *   <li>{@code questionCount}: pytania, które użytkownik może teraz rozwiązywać (pule płaskie i przeczytane rozdziały),</li>
 *   <li>{@code solvedCount}: ile z nich rozwiązał co najmniej raz w teście,</li>
 *   <li>{@code lockedCount}: pytania z nieprzeczytanych rozdziałów podręczników, które odblokują się po przeczytaniu.</li>
 * </ul>
 */
public record TestCatalogResponse(List<CategoryNode> categories) {

    public record CategoryNode(
            Long id, String name, String slug, String itemsLabel, int sortOrder,
            int questionCount, int solvedCount, int lockedCount,
            List<SubjectNode> subjects
    ) {
    }

    public record SubjectNode(
            Long id, String name, String slug,
            int questionCount, int solvedCount, int lockedCount,
            List<SourceNode> sources
    ) {
    }

    public record SourceNode(
            Long id, String name, SourceType sourceType,
            int questionCount, int solvedCount, int lockedCount
    ) {
    }
}
