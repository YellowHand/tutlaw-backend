package pl.gradusiuris.backend.admin;

import java.util.List;
import java.util.Map;

/** Odczytany plik importu: wiersze, domyślne wartości z pliku (JSON: akt i źródło dla całej listy) i pominięte nieznane kolumny. */
record ParsedImport(List<ImportRow> rows, Map<ImportField, String> defaults, List<String> ignoredColumns) {
}
