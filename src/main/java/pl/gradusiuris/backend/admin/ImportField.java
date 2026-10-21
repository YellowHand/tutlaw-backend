package pl.gradusiuris.backend.admin;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Pola pytania w pliku importu. Nagłówek kolumny (CSV) albo nazwa pola (JSON) rozpoznajemy po uproszczeniu: małe litery,
 * bez polskich znaków, bez spacji i znaków innych niż litery i cyfry. Dzięki temu „Podstawa prawna", {@code podstawa_prawna}
 * i {@code legalBasis} to to samo. Są nazwy polskie i angielskie.
 */
enum ImportField {
    ACT("akt", "aktprawny", "ustawa", "act", "legalact"),
    SOURCE("zrodlo", "arkusz", "source", "sheet"),
    BODY("tresc", "trescpytania", "pytanie", "body", "question"),
    OPTION_A("a", "odpowiedza", "odpa", "optiona", "answera"),
    OPTION_B("b", "odpowiedzb", "odpb", "optionb", "answerb"),
    OPTION_C("c", "odpowiedzc", "odpc", "optionc", "answerc"),
    CORRECT("poprawna", "poprawnaodpowiedz", "odpowiedzpoprawna", "correct", "correctoption", "answer"),
    EXPLANATION("wyjasnienie", "uzasadnienie", "explanation"),
    LEGAL_BASIS("podstawa", "podstawaprawna", "legalbasis"),
    SECTION("rozdzial", "section"),
    VALID_AS_OF("obowiazujeod", "stanprawnyna", "validasof"),
    LAW_CHANGED_AT("zmianawprawie", "datazmiany", "datazmianywprawie", "lawchangedat"),
    LAW_CHANGE_NOTE("opiszmiany", "opiszmianywprawie", "lawchangenote");

    private final String[] aliases;

    ImportField(String... aliases) {
        this.aliases = aliases;
    }

    /** Pole o podanej nazwie nagłówka albo null, gdy nazwa jest nieznana. */
    static ImportField byHeader(String header) {
        String key = simplify(header);
        for (ImportField field : values()) {
            for (String alias : field.aliases) {
                if (alias.equals(key)) {
                    return field;
                }
            }
        }
        return null;
    }

    /** Nazwa do porównań: małe litery, bez znaków diakrytycznych, tylko litery i cyfry. */
    static String simplify(String text) {
        if (text == null) {
            return "";
        }
        String lower = text.toLowerCase(Locale.ROOT).replace('ł', 'l');
        return Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("[^a-z0-9]+", "");
    }
}
