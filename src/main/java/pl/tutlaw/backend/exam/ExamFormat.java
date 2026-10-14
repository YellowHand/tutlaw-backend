package pl.tutlaw.backend.exam;

/**
 * Stały format egzaminu próbnego: zawsze {@value #QUESTION_COUNT} pytań z całego dostępnego materiału, a zaliczenie od
 * {@value #PASS_POINTS} punktów. Próg ustala serwer; użytkownik nie wybiera ani zakresu, ani liczby pytań, ani progu.
 * Ćwiczenie z jednego działu to osobna funkcja (własny test), która nie ocenia, czy ktoś zdał.
 */
public final class ExamFormat {

    /** Tyle pytań ma pełny egzamin. */
    public static final int QUESTION_COUNT = 150;

    /** Tyle punktów (poprawnych odpowiedzi) trzeba mieć, żeby zdać pełny egzamin. */
    public static final int PASS_POINTS = 100;

    private ExamFormat() {
    }

    /**
     * Próg zaliczenia w punktach dla egzaminu o {@code total} pytaniach. Pełny egzamin (150 pytań) to zawsze
     * {@value #PASS_POINTS}. Gdy w materiale jest mniej pytań, egzamin jest krótszy, a próg to ta sama część
     * ({@value #PASS_POINTS} z {@value #QUESTION_COUNT}), zaokrąglona w górę do całego punktu.
     */
    public static int passPointsFor(int total) {
        if (total <= 0) {
            return 0;
        }
        return (int) (((long) total * PASS_POINTS + QUESTION_COUNT - 1) / QUESTION_COUNT);
    }
}
