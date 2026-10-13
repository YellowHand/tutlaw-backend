package pl.tutlaw.backend.card;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reguły poprawności treści karty oraz klucz do wykrywania duplikatów.
 * Czysta logika bez Springa i bazy, więc łatwa do przetestowania.
 */
final class CardContentRules {

    private static final char KEY_SEPARATOR = '\u0001';

    private CardContentRules() {
    }

    /** Jak {@link #validate(boolean, boolean, String, String, Integer, String, String, String, CorrectOption, Long)} bez powiązania. */
    static List<String> validate(
            boolean textbookSource,
            boolean hasChapter,
            String front,
            String back,
            Integer pageNumber,
            String optionA,
            String optionB,
            String optionC,
            CorrectOption correctOption
    ) {
        return validate(textbookSource, hasChapter, front, back, pageNumber,
                optionA, optionB, optionC, correctOption, null);
    }

    /**
     * @param textbookSource czy karta należy do źródła typu podręcznik (wtedy musi mieć rozdział)
     * @param linkedTestCardId pytanie testowe, z którym fiszka jest powiązana (null, gdy karta nie jest powiązana)
     * @return lista błędów po polsku; pusta, gdy karta jest poprawna
     */
    static List<String> validate(
            boolean textbookSource,
            boolean hasChapter,
            String front,
            String back,
            Integer pageNumber,
            String optionA,
            String optionB,
            String optionC,
            CorrectOption correctOption,
            Long linkedTestCardId
    ) {
        List<String> problems = new ArrayList<>();
        boolean anyTestField = !isBlank(optionA) || !isBlank(optionB) || !isBlank(optionC) || correctOption != null;

        if (isBlank(front)) {
            problems.add("Treść pytania (front) nie może być pusta.");
        }
        // Pytanie testowe ma poprawną odpowiedź w correctOption, więc back jest wymagane tylko dla fiszki.
        if (!anyTestField && isBlank(back)) {
            problems.add("Odpowiedź (back) nie może być pusta.");
        }
        if (pageNumber != null && pageNumber < 0) {
            problems.add("Numer strony nie może być ujemny.");
        }
        if (textbookSource && !hasChapter) {
            problems.add("Karta z podręcznika musi należeć do rozdziału, inaczej nigdy nie trafi do nauki.");
        }

        if (anyTestField) {
            if (isBlank(optionA) || isBlank(optionB) || isBlank(optionC)) {
                problems.add("Pytanie testowe wymaga trzech odpowiedzi: A, B i C.");
            } else if (normalize(optionA).equals(normalize(optionB))
                    || normalize(optionA).equals(normalize(optionC))
                    || normalize(optionB).equals(normalize(optionC))) {
                problems.add("Odpowiedzi A, B i C muszą się od siebie różnić.");
            }
            if (correctOption == null) {
                problems.add("Pytanie testowe wymaga wskazania poprawnej odpowiedzi (A, B lub C).");
            }
            if (linkedTestCardId != null) {
                problems.add("Powiązanie z pytaniem testowym ma tylko fiszka, a nie samo pytanie testowe.");
            }
        }

        return problems;
    }

    static final int LEGAL_BASIS_MAX_LENGTH = 500;

    /** Podstawa prawna jest opcjonalna, ale ma limit długości (taki sam jak ograniczenie w bazie). Zwraca opis błędu albo null. */
    static String legalBasisProblem(String legalBasis) {
        if (legalBasis == null) return null;
        String trimmed = legalBasis.strip();
        if (trimmed.codePointCount(0, trimmed.length()) > LEGAL_BASIS_MAX_LENGTH) {
            return "Podstawa prawna może mieć najwyżej " + LEGAL_BASIS_MAX_LENGTH + " znaków.";
        }
        return null;
    }

    static final int MIN_EXAM_YEAR = 1990;
    static final int MAX_EXAM_YEAR = 2100;

    /**
     * Pochodzenie i rok egzaminu muszą do siebie pasować (taka sama reguła jak ograniczenie w bazie): pytanie autorskie
     * nie ma roku, pytanie z aplikacji musi go mieć. Zwraca opis błędu albo null.
     */
    static String originProblem(QuestionOrigin origin, Integer examYear) {
        QuestionOrigin effective = origin == null ? QuestionOrigin.AUTHORED : origin;
        if (!effective.requiresYear()) {
            return examYear == null ? null : "Pytanie autorskie nie ma roku egzaminu.";
        }
        if (examYear == null) {
            return "Dla pytania z aplikacji podaj rok egzaminu (examYear).";
        }
        if (examYear < MIN_EXAM_YEAR || examYear > MAX_EXAM_YEAR) {
            return "Rok egzaminu musi być z zakresu " + MIN_EXAM_YEAR + "–" + MAX_EXAM_YEAR + ".";
        }
        return null;
    }

    /** Pytanie testowe to takie, które ma poprawną odpowiedź (i, po walidacji, komplet opcji). */
    static boolean isTest(CorrectOption correctOption) {
        return correctOption != null;
    }

    /**
     * Klucz duplikatu. Dwie karty w tym samym źródle są duplikatem, gdy mają to samo pytanie
     * i tę samą odpowiedź. Dla pytań testowych liczy się komplet opcji i poprawna odpowiedź
     * (treść pola back jest wtedy bez znaczenia), bo to ona decyduje, czy pytanie jest „takie samo".
     * Wielkość liter, nadmiarowe spacje i końcowa interpunkcja nie rozróżniają kart.
     */
    static String duplicateKey(
            String front, String back, String optionA, String optionB, String optionC, CorrectOption correctOption) {
        return duplicateKey(front, back, optionA, optionB, optionC, correctOption, QuestionOrigin.AUTHORED, null);
    }

    /**
     * Jak {@link #duplicateKey(String, String, String, String, String, CorrectOption)}, ale do klucza wchodzi też
     * pochodzenie i rok. To samo pytanie z innego roku albo z innej aplikacji jest więc osobnym pytaniem
     * (w kolejnych latach pytania się powtarzają, czasem z inną kolejnością odpowiedzi), a duplikatem jest dopiero
     * pytanie identyczne także pod względem pochodzenia i roku.
     */
    static String duplicateKey(
            String front, String back, String optionA, String optionB, String optionC, CorrectOption correctOption,
            QuestionOrigin origin, Integer examYear) {
        StringBuilder key = new StringBuilder(normalize(front));
        if (origin != null && origin != QuestionOrigin.AUTHORED) {
            key.append(KEY_SEPARATOR).append("O").append(origin.name()).append(KEY_SEPARATOR).append(examYear);
        }
        if (isTest(correctOption)) {
            key.append(KEY_SEPARATOR).append("T")
                    .append(KEY_SEPARATOR).append(normalize(optionA))
                    .append(KEY_SEPARATOR).append(normalize(optionB))
                    .append(KEY_SEPARATOR).append(normalize(optionC))
                    .append(KEY_SEPARATOR).append(correctOption.name());
        } else {
            key.append(KEY_SEPARATOR).append("F")
                    .append(KEY_SEPARATOR).append(normalize(back));
        }
        return key.toString();
    }

    /** Małe litery, pojedyncze spacje, bez spacji na brzegach i bez końcowej kropki, średnika itp. */
    static String normalize(String text) {
        if (text == null) return "";
        String collapsed = text.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        int end = collapsed.length();
        while (end > 0 && ".;:,!".indexOf(collapsed.charAt(end - 1)) >= 0) {
            end--;
        }
        return collapsed.substring(0, end).strip();
    }

    /** Puste lub same białe znaki traktujemy jak brak wartości. */
    static String blankToNull(String text) {
        return isBlank(text) ? null : text.strip();
    }

    static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
