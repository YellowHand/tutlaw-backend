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

    /**
     * @param textbookSource czy karta należy do źródła typu podręcznik (wtedy musi mieć rozdział)
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
            CorrectOption correctOption
    ) {
        List<String> problems = new ArrayList<>();

        if (isBlank(front)) {
            problems.add("Treść pytania (front) nie może być pusta.");
        }
        if (isBlank(back)) {
            problems.add("Odpowiedź (back) nie może być pusta.");
        }
        if (pageNumber != null && pageNumber < 0) {
            problems.add("Numer strony nie może być ujemny.");
        }
        if (textbookSource && !hasChapter) {
            problems.add("Karta z podręcznika musi należeć do rozdziału, inaczej nigdy nie trafi do nauki.");
        }

        boolean anyTestField = !isBlank(optionA) || !isBlank(optionB) || !isBlank(optionC) || correctOption != null;
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
        }

        return problems;
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
        StringBuilder key = new StringBuilder(normalize(front));
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
