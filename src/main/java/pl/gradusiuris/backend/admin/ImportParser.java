package pl.gradusiuris.backend.admin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Odczyt pliku importu pytań (CSV albo JSON) do wierszy z polami. Nie sprawdza treści pytań ani nie zagląda do bazy:
 * to robi {@code QuestionImportService}. Błąd budowy pliku (zepsuty JSON, brak kolumn, za dużo wierszy) to
 * {@link IllegalArgumentException} po polsku.
 *
 * <p>CSV: pierwszy wiersz to nagłówek; separator (średnik, przecinek albo tabulator) rozpoznajemy sami, bo polski Excel
 * zapisuje średnikami; pola z separatorem, cudzysłowem albo nową linią ujmuje się w cudzysłów ({@code ""} to cudzysłów).
 * JSON: lista obiektów albo obiekt z listą {@code questions}; odpowiedzi jako pola {@code a}, {@code b}, {@code c} albo lista
 * {@code options}; {@code akt} i {@code zrodlo} można podać raz na górze dla całej listy.
 */
final class ImportParser {

    static final int MAX_ROWS = 5000;
    private static final char[] DELIMITERS = {';', ',', '\t'};
    private static final EnumSet<ImportField> REQUIRED =
            EnumSet.of(ImportField.BODY, ImportField.OPTION_A, ImportField.OPTION_B, ImportField.OPTION_C, ImportField.CORRECT);
    private static final Set<String> OPTION_LISTS = Set.of("options", "opcje", "odpowiedzi", "answers");
    private static final Set<String> QUESTION_LISTS = Set.of("questions", "pytania");

    private ImportParser() {
    }

    // ------------------------------------------------------------------------------------------------------- CSV

    static ParsedImport parseCsv(String content) {
        String text = content.startsWith("﻿") ? content.substring(1) : content;
        List<List<String>> records = csvRecords(text, detectDelimiter(text));
        int headerIndex = -1;
        for (int i = 0; i < records.size(); i++) {
            if (!isBlank(records.get(i))) {
                headerIndex = i;
                break;
            }
        }
        if (headerIndex < 0) {
            throw new IllegalArgumentException("Plik jest pusty: brakuje wiersza z nagłówkami kolumn.");
        }
        List<String> header = records.get(headerIndex);
        ImportField[] columns = new ImportField[header.size()];
        List<String> ignored = new ArrayList<>();
        Set<ImportField> present = EnumSet.noneOf(ImportField.class);
        for (int i = 0; i < header.size(); i++) {
            String name = header.get(i).strip();
            if (name.isEmpty()) {
                continue;
            }
            ImportField field = ImportField.byHeader(name);
            if (field == null) {
                ignored.add(name);
            } else if (!present.add(field)) {
                throw new IllegalArgumentException("Kolumna „" + name + "” występuje w pliku więcej niż raz.");
            } else {
                columns[i] = field;
            }
        }
        requireColumns(present);

        List<ImportRow> rows = new ArrayList<>();
        for (int i = headerIndex + 1; i < records.size(); i++) {
            List<String> record = records.get(i);
            if (isBlank(record)) {
                continue;
            }
            if (rows.size() >= MAX_ROWS) {
                throw tooMany();
            }
            Map<ImportField, String> values = new EnumMap<>(ImportField.class);
            for (int c = 0; c < record.size() && c < columns.length; c++) {
                if (columns[c] != null) {
                    values.put(columns[c], clean(record.get(c)));
                }
            }
            String problem = record.size() > columns.length && !record.subList(columns.length, record.size()).stream().allMatch(String::isBlank)
                    ? "Wiersz ma więcej pól niż nagłówek (sprawdź cudzysłowy i średniki w treści)." : null;
            rows.add(new ImportRow(i + 1, values, problem));
        }
        return new ParsedImport(rows, new EnumMap<>(ImportField.class), ignored);
    }

    private static char detectDelimiter(String text) {
        int[] counts = new int[DELIMITERS.length];
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (!quoted && (c == '\n' || c == '\r')) {
                if (counts[0] + counts[1] + counts[2] > 0) {
                    break;
                }
            } else if (!quoted) {
                for (int d = 0; d < DELIMITERS.length; d++) {
                    if (c == DELIMITERS[d]) {
                        counts[d]++;
                    }
                }
            }
        }
        int best = 0;
        for (int d = 1; d < DELIMITERS.length; d++) {
            if (counts[d] > counts[best]) {
                best = d;
            }
        }
        return DELIMITERS[best];
    }

    private static List<List<String>> csvRecords(String text, char delimiter) {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean wasQuoted = false;
        int quoteStartRecord = 0;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"' && field.toString().isBlank()) {
                quoted = true;
                wasQuoted = true;
                field.setLength(0);
                quoteStartRecord = records.size() + 1;
            } else if (c == delimiter) {
                record.add(field.toString());
                field.setLength(0);
                wasQuoted = false;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                record.add(field.toString());
                field.setLength(0);
                wasQuoted = false;
                records.add(record);
                if (records.size() > MAX_ROWS + 100) {
                    throw tooMany();
                }
                record = new ArrayList<>();
            } else {
                field.append(c);
            }
            i++;
        }
        if (quoted) {
            throw new IllegalArgumentException("W pliku CSV jest niezamknięty cudzysłów (zaczyna się w wierszu " + quoteStartRecord + ").");
        }
        if (field.length() > 0 || wasQuoted || !record.isEmpty()) {
            record.add(field.toString());
            records.add(record);
        }
        return records;
    }

    private static boolean isBlank(List<String> record) {
        return record.stream().allMatch(s -> s.isBlank());
    }

    // ------------------------------------------------------------------------------------------------------ JSON

    static ParsedImport parseJson(String content) {
        Object root = MiniJson.parse(content.startsWith("﻿") ? content.substring(1) : content);
        Map<ImportField, String> defaults = new EnumMap<>(ImportField.class);
        Set<String> ignored = new LinkedHashSet<>();
        List<?> items;
        if (root instanceof List<?> list) {
            items = list;
        } else if (root instanceof Map<?, ?> map) {
            items = null;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (QUESTION_LISTS.contains(ImportField.simplify(key))) {
                    if (!(entry.getValue() instanceof List<?> list)) {
                        throw new IllegalArgumentException("Pole „" + key + "” w pliku JSON musi być listą pytań.");
                    }
                    items = list;
                    continue;
                }
                ImportField field = ImportField.byHeader(key);
                if ((field == ImportField.ACT || field == ImportField.SOURCE) && (entry.getValue() instanceof String
                        || entry.getValue() instanceof Number)) {
                    defaults.put(field, clean(String.valueOf(entry.getValue())));
                } else {
                    ignored.add(key);
                }
            }
            if (items == null) {
                throw new IllegalArgumentException("W pliku JSON brakuje listy pytań (pole „questions” albo „pytania”).");
            }
        } else {
            throw new IllegalArgumentException("Plik JSON musi zawierać listę pytań albo obiekt z polem „questions”.");
        }
        if (items.size() > MAX_ROWS) {
            throw tooMany();
        }
        List<ImportRow> rows = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            rows.add(jsonRow(i + 1, items.get(i), ignored));
        }
        return new ParsedImport(rows, defaults, new ArrayList<>(ignored));
    }

    private static ImportRow jsonRow(int number, Object item, Set<String> ignored) {
        Map<ImportField, String> values = new EnumMap<>(ImportField.class);
        if (!(item instanceof Map<?, ?> map)) {
            return new ImportRow(number, values, "Pozycja nie jest obiektem z polami pytania.");
        }
        String problem = null;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (OPTION_LISTS.contains(ImportField.simplify(key)) && value instanceof List<?> options) {
                if (options.size() != 3) {
                    problem = "Pole „" + key + "” musi mieć dokładnie trzy odpowiedzi (A, B, C).";
                } else {
                    ImportField[] targets = {ImportField.OPTION_A, ImportField.OPTION_B, ImportField.OPTION_C};
                    for (int i = 0; i < 3; i++) {
                        values.put(targets[i], scalar(options.get(i)));
                    }
                }
                continue;
            }
            ImportField field = ImportField.byHeader(key);
            if (field == null) {
                ignored.add(key);
            } else if (value instanceof Map<?, ?> || value instanceof List<?>) {
                problem = "Pole „" + key + "” ma nieprawidłowy typ (oczekiwano tekstu).";
            } else {
                values.put(field, scalar(value));
            }
        }
        return new ImportRow(number, values, problem);
    }

    private static String scalar(Object value) {
        return value == null || value instanceof Map<?, ?> || value instanceof List<?> ? null : clean(String.valueOf(value));
    }

    // ----------------------------------------------------------------------------------------------- wspólne

    private static void requireColumns(Set<ImportField> present) {
        List<String> missing = new ArrayList<>();
        for (ImportField field : REQUIRED) {
            if (!present.contains(field)) {
                missing.add(switch (field) {
                    case BODY -> "treść";
                    case OPTION_A -> "A";
                    case OPTION_B -> "B";
                    case OPTION_C -> "C";
                    default -> "poprawna";
                });
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("W pliku brakuje kolumn: " + String.join(", ", missing)
                    + ". Pierwszy wiersz musi zawierać nazwy kolumn.");
        }
    }

    private static String clean(String text) {
        return QuestionRules.clean(text);
    }

    private static IllegalArgumentException tooMany() {
        return new IllegalArgumentException("W jednym pliku może być najwyżej " + MAX_ROWS + " pytań. Podziel plik na części.");
    }
}
