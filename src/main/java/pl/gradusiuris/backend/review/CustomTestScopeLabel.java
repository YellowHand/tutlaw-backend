package pl.gradusiuris.backend.review;

import java.util.List;

/** Nazwa zakresu własnego testu na liście "Moje testy", złożona z nazw wybranych dziedzin i aktów prawnych. */
final class CustomTestScopeLabel {

    static final String EVERYTHING = "Cała pula";
    static final int MAX_LENGTH = 300;

    private CustomTestScopeLabel() {
    }

    /**
     * Bez wyboru cała pula; do trzech nazw wymienia wszystkie ("A", "A i B", "A, B i C"); dalej dwie pierwsze
     * i liczba pozostałych ("A, B i 2 inne", "A, B i 5 innych").
     */
    static String describe(List<String> names) {
        if (names.isEmpty()) {
            return EVERYTHING;
        }
        String label;
        if (names.size() == 1) {
            label = names.get(0);
        } else if (names.size() == 2) {
            label = names.get(0) + " i " + names.get(1);
        } else if (names.size() == 3) {
            label = names.get(0) + ", " + names.get(1) + " i " + names.get(2);
        } else {
            int rest = names.size() - 2;
            label = names.get(0) + ", " + names.get(1) + " i " + rest + (isFewForm(rest) ? " inne" : " innych");
        }
        return label.length() <= MAX_LENGTH ? label : label.substring(0, MAX_LENGTH - 1) + "…";
    }

    /** 2-4, 22-24 itd., ale nie 12-14. */
    private static boolean isFewForm(int n) {
        int lastTwo = n % 100;
        int last = n % 10;
        return last >= 2 && last <= 4 && !(lastTwo >= 12 && lastTwo <= 14);
    }
}
