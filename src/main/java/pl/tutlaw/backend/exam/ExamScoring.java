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
     * Zaliczone, gdy wynik jest co najmniej równy progowi. Porównanie na liczbach całkowitych, a nie na
     * zaokrąglonym procencie: 112 z 150 to 74,7%, więc przy progu 75% to jeszcze nie jest zaliczenie.
     */
    static boolean passed(int correct, int total, int thresholdPercent) {
        return total > 0 && (long) correct * 100 >= (long) thresholdPercent * total;
    }
}
