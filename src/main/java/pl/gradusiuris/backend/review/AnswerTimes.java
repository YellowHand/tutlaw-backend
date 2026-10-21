package pl.gradusiuris.backend.review;

/**
 * Jak liczymy czas odpowiadania w podsumowaniach. Pojedyncza odpowiedź liczy się najwyżej tyle, ile
 * {@link #CAP_SECONDS}: pytanie zostawione otwarte na kilka minut nie psuje średniej ani sumy czasu testu.
 * To czas odpowiadania na pytanie, osobny od czasu nauki (ten liczy się razem z czytaniem wyjaśnień, zob. StudyTimeService).
 */
public final class AnswerTimes {

    public static final double CAP_SECONDS = 120;

    private AnswerTimes() {
    }

    /** Czas jednej odpowiedzi do podsumowań: brak i wartości ujemne to 0, długie przycinamy do {@link #CAP_SECONDS}. */
    public static double capped(Double seconds) {
        if (seconds == null || seconds < 0) {
            return 0;
        }
        return Math.min(seconds, CAP_SECONDS);
    }
}
