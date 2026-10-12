package pl.tutlaw.backend.category.dto;

/**
 * @param itemsLabel   jak nazywać zawartość kategorii („Przedmioty", „Akty prawne")
 * @param sortOrder    kolejność wyświetlania
 * @param subjectCount ile przedmiotów/aktów prawnych jest w kategorii (puste kategorie można ukryć w widoku)
 */
public record CategoryResponse(
        Long id, String name, String slug, String itemsLabel, int sortOrder, int subjectCount
) {
}
