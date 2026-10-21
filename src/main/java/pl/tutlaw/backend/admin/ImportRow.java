package pl.tutlaw.backend.admin;

import java.util.Map;

/**
 * Jedno pytanie z pliku importu: numer wiersza (CSV: jak w arkuszu, nagłówek to wiersz 1; JSON: numer pozycji), wartości
 * pól i ewentualny błąd odczytu (np. pole o złym typie w JSON), który trafia do raportu jako błąd tego wiersza.
 */
record ImportRow(int number, Map<ImportField, String> values, String problem) {

    String get(ImportField field) {
        return values.get(field);
    }
}
