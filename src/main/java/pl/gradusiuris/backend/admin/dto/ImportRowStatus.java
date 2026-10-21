package pl.gradusiuris.backend.admin.dto;

/** Wynik sprawdzenia jednego wiersza importu: poprawny, powtórzenie (pomijane) albo błąd (blokuje zapis całego pliku). */
public enum ImportRowStatus {
    OK,
    DUPLICATE,
    ERROR
}
