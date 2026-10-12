package pl.tutlaw.backend.card.dto;

import java.util.List;

/**
 * Wynik importu zbiorczego.
 *
 * @param created    ile kart zapisano
 * @param duplicates ile wierszy pominięto, bo identyczna karta już istnieje w źródle (lub wcześniej w tym samym pliku)
 * @param errors     błędy poszczególnych wierszy (numer wiersza liczony od 1); takie wiersze nie zostały zapisane
 */
public record BulkImportResult(int created, int duplicates, List<String> errors) {
}
