package pl.gradusiuris.backend.admin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mały parser JSON (RFC 8259) do importu pytań. Zwraca {@link Map} (obiekt), {@link List} (tablica), {@link String},
 * {@link Boolean}, null, a liczby jako {@link String} z oryginalnym zapisem (importowi liczby i tak służą za tekst).
 * Własny parser, bo import ma dać zrozumiały błąd z miejscem w tekście i nie zależeć od wersji biblioteki.
 */
final class MiniJson {

    private static final int MAX_DEPTH = 20;

    private final String text;
    private int pos;

    private MiniJson(String text) {
        this.text = text;
    }

    /** Parsuje cały tekst (jedna wartość i nic więcej). Błąd to {@link IllegalArgumentException} po polsku. */
    static Object parse(String text) {
        MiniJson parser = new MiniJson(text);
        parser.skipSpace();
        Object value = parser.value(0);
        parser.skipSpace();
        if (parser.pos < text.length()) {
            throw parser.error("po zakończeniu danych jest jeszcze tekst");
        }
        return value;
    }

    private Object value(int depth) {
        if (depth > MAX_DEPTH) {
            throw error("dane są zagnieżdżone zbyt głęboko");
        }
        if (pos >= text.length()) {
            throw error("dane urwały się w połowie");
        }
        char c = text.charAt(pos);
        switch (c) {
            case '{':
                return object(depth);
            case '[':
                return array(depth);
            case '"':
                return string();
            case 't':
                return literal("true", Boolean.TRUE);
            case 'f':
                return literal("false", Boolean.FALSE);
            case 'n':
                return literal("null", null);
            default:
                if (c == '-' || (c >= '0' && c <= '9')) {
                    return number();
                }
                throw error("nieoczekiwany znak „" + c + "”");
        }
    }

    private Map<String, Object> object(int depth) {
        Map<String, Object> result = new LinkedHashMap<>();
        pos++;
        skipSpace();
        if (peek('}')) {
            pos++;
            return result;
        }
        while (true) {
            skipSpace();
            if (!peek('"')) {
                throw error("oczekiwano nazwy pola w cudzysłowie");
            }
            String key = string();
            skipSpace();
            expect(':');
            skipSpace();
            result.put(key, value(depth + 1));
            skipSpace();
            if (peek(',')) {
                pos++;
            } else if (peek('}')) {
                pos++;
                return result;
            } else {
                throw error("oczekiwano przecinka albo }");
            }
        }
    }

    private List<Object> array(int depth) {
        List<Object> result = new ArrayList<>();
        pos++;
        skipSpace();
        if (peek(']')) {
            pos++;
            return result;
        }
        while (true) {
            skipSpace();
            result.add(value(depth + 1));
            skipSpace();
            if (peek(',')) {
                pos++;
            } else if (peek(']')) {
                pos++;
                return result;
            } else {
                throw error("oczekiwano przecinka albo ]");
            }
        }
    }

    private String string() {
        StringBuilder out = new StringBuilder();
        pos++;
        while (pos < text.length()) {
            char c = text.charAt(pos++);
            if (c == '"') {
                return out.toString();
            }
            if (c < 0x20) {
                pos--;
                throw error("znak sterujący w tekście (nowa linia wewnątrz tekstu zapisuje się jako \\n)");
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (pos >= text.length()) {
                break;
            }
            char e = text.charAt(pos++);
            switch (e) {
                case '"', '\\', '/' -> out.append(e);
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    if (pos + 4 > text.length()) {
                        throw error("niepełny kod \\u");
                    }
                    try {
                        out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                    } catch (NumberFormatException ex) {
                        throw error("nieprawidłowy kod \\u");
                    }
                    pos += 4;
                }
                default -> {
                    pos--;
                    throw error("nieznana sekwencja \\" + e);
                }
            }
        }
        throw error("tekst w cudzysłowie nie został zamknięty");
    }

    private String number() {
        int start = pos;
        if (peek('-')) {
            pos++;
        }
        while (pos < text.length() && "0123456789.eE+-".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
        String literal = text.substring(start, pos);
        try {
            Double.parseDouble(literal);
        } catch (NumberFormatException ex) {
            pos = start;
            throw error("nieprawidłowa liczba");
        }
        return literal;
    }

    private Object literal(String word, Object value) {
        if (!text.startsWith(word, pos)) {
            throw error("nieoczekiwany znak „" + text.charAt(pos) + "”");
        }
        pos += word.length();
        return value;
    }

    private boolean peek(char c) {
        return pos < text.length() && text.charAt(pos) == c;
    }

    private void expect(char c) {
        if (!peek(c)) {
            throw error("oczekiwano znaku „" + c + "”");
        }
        pos++;
    }

    private void skipSpace() {
        while (pos < text.length() && " \t\r\n".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
    }

    private IllegalArgumentException error(String what) {
        int line = 1;
        int column = 1;
        for (int i = 0; i < Math.min(pos, text.length()); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return new IllegalArgumentException("Nieprawidłowy plik JSON: " + what + " (wiersz " + line + ", kolumna " + column + ").");
    }
}
