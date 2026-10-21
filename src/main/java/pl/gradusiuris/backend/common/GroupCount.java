package pl.gradusiuris.backend.common;

/**
 * Wynik zapytania typu „ile czego w danej grupie" (np. kart w rozdziale).
 * Używane jako projekcja Spring Data, więc zapytania nie ładują całych encji.
 */
public interface GroupCount {

    Long getGroupId();

    Long getTotal();
}
