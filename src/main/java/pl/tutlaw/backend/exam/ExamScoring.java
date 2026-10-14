package pl.tutlaw.backend.exam;

/** Punktacja egzaminu: jeden punkt za poprawną odpowiedź, brak punktów ujemnych, brak odpowiedzi = 0 punktów. */
final class ExamScoring {

    private ExamScoring() {
    }

    /** Wynik w procentach, zaokrąglony do jednego miejsca po przecinku (do wyświetlania). */
    static double scorePercent(int correct, int total) {
        if (total <= 0) {
            return 0.0;
        }
        return Math.round(correct * 1000.0 / total) / 10.0;
    }

    /**
     * Zaliczone, gdy liczba punktów jest co najmniej równa progowi formatu egzaminu ({@link ExamFormat#passPointsFor}):
     * 100 z 150, a przy krótszym egzaminie proporcjonalnie. Porównanie na liczbach całkowitych, nie na procentach.
     */
    static boolean passed(int correct, int total) {
        return total > 0 && correct >= ExamFormat.passPointsFor(total);
    }

    /**
     * Najmniejszy cały procent, który odpowiada progowi w punktach (dla pola {@code pass_threshold_percent}, które
     * zostało po wcześniejszych etapach). 100 z 150 to 66,7%, więc zapisujemy 67.
     */
    static int thresholdPercent(int passPoints, int total) {
        if (total <= 0) {
            return 100;
        }
        long percent = ((long) passPoints * 100 + total - 1) / total;
        return (int) Math.max(1, Math.min(100, percent));
    }
}
