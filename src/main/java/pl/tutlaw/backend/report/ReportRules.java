package pl.tutlaw.backend.report;

/** Reguły zgłoszeń błędów w pytaniach. Czysta logika, bez Springa i bazy. */
final class ReportRules {

    static final int COMMENT_MAX_LENGTH = 1000;
    /** Tyle nowych zgłoszeń (nie licząc uzupełnień już wysłanych) użytkownik może wysłać w ciągu doby. */
    static final int DAILY_LIMIT = 20;

    private ReportRules() {
    }

    /** Komentarz bez białych znaków na brzegach; pusty to brak komentarza. */
    static String normalizeComment(String comment) {
        if (comment == null) return null;
        String trimmed = comment.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Opis błędu po polsku albo null, gdy zgłoszenie jest poprawne. */
    static String problem(CardReportReason reason, String comment) {
        if (reason == null) {
            return "Wybierz powód zgłoszenia.";
        }
        String normalized = normalizeComment(comment);
        if (normalized != null && normalized.codePointCount(0, normalized.length()) > COMMENT_MAX_LENGTH) {
            return "Komentarz może mieć najwyżej " + COMMENT_MAX_LENGTH + " znaków.";
        }
        if (reason == CardReportReason.OTHER && normalized == null) {
            return "Opisz krótko, co jest nie tak.";
        }
        return null;
    }
}
