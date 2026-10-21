package pl.gradusiuris.backend.admin;

import pl.gradusiuris.backend.question.CorrectOption;

import java.time.LocalDate;

/**
 * Reguły treści pytania: wspólne dla ręcznego dodawania i edycji oraz dla importu. Czysta logika, bez Springa i bazy.
 * Komunikaty są po polsku i trafiają do administratora w niezmienionej postaci.
 */
final class QuestionRules {

    static final int LEGAL_BASIS_MAX = 500;
    static final int LAW_CHANGE_NOTE_MAX = 500;

    private QuestionRules() {
    }

    /** Tekst bez białych znaków na brzegach; pusty to brak (null). */
    static String clean(String text) {
        if (text == null) {
            return null;
        }
        String stripped = text.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    /**
     * Sprawdza pola pytania i zwraca oczyszczony szkic. Błąd to {@link IllegalArgumentException} z opisem po polsku.
     * Odpowiedzi muszą się różnić (dwie takie same dałyby pytanie z dwiema poprawnymi odpowiedziami).
     */
    static QuestionDraft draft(String body, String optionA, String optionB, String optionC, CorrectOption correct,
                               String explanation, String legalBasis, LocalDate validAsOf, LocalDate lawChangedAt,
                               String lawChangeNote) {
        String cleanBody = require(body, "Wpisz treść pytania.");
        String a = require(optionA, "Wpisz odpowiedź A.");
        String b = require(optionB, "Wpisz odpowiedź B.");
        String c = require(optionC, "Wpisz odpowiedź C.");
        if (correct == null) {
            throw new IllegalArgumentException("Wskaż poprawną odpowiedź (A, B albo C).");
        }
        if (same(a, b) || same(a, c) || same(b, c)) {
            throw new IllegalArgumentException("Odpowiedzi A, B i C muszą się różnić.");
        }
        String basis = clean(legalBasis);
        if (basis != null && basis.codePointCount(0, basis.length()) > LEGAL_BASIS_MAX) {
            throw new IllegalArgumentException("Podstawa prawna może mieć najwyżej " + LEGAL_BASIS_MAX + " znaków.");
        }
        String note = clean(lawChangeNote);
        if (note != null && note.codePointCount(0, note.length()) > LAW_CHANGE_NOTE_MAX) {
            throw new IllegalArgumentException("Opis zmiany w prawie może mieć najwyżej " + LAW_CHANGE_NOTE_MAX + " znaków.");
        }
        if (note != null && lawChangedAt == null) {
            throw new IllegalArgumentException("Opis zmiany w prawie wymaga daty zmiany.");
        }
        return new QuestionDraft(cleanBody, a, b, c, correct, clean(explanation), basis, validAsOf, lawChangedAt, note);
    }

    /**
     * Klucz do wykrywania powtórzeń: treść bez różnic w odstępach i wielkości liter. Zawsze łapie to, co złapałby
     * indeks unikalny w bazie (identyczna treść), a dodatkowo pytania różniące się tylko odstępami.
     */
    static String duplicateKey(String body) {
        if (body == null) {
            return "";
        }
        return body.strip().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
    }

    private static String require(String text, String message) {
        String cleaned = clean(text);
        if (cleaned == null) {
            throw new IllegalArgumentException(message);
        }
        return cleaned;
    }

    private static boolean same(String x, String y) {
        return duplicateKey(x).equals(duplicateKey(y));
    }
}
