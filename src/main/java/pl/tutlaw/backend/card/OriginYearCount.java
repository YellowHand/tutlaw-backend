package pl.tutlaw.backend.card;

/**
 * Wynik zapytania „ile czego w danym pochodzeniu i roku egzaminu" (rok jest pusty dla pytań autorskich).
 * Projekcja Spring Data, więc zapytania nie ładują całych encji.
 */
public interface OriginYearCount {

    QuestionOrigin getOrigin();

    Integer getExamYear();

    Long getTotal();
}
