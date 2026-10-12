package pl.tutlaw.backend.review;

/**
 * Reguły planowania terminów, które nie zależą od bazy ani od Springa (łatwe do przetestowania):
 * tryb egzaminacyjny, rozrzut terminów (fuzz) i plan nowych pytań do egzaminu.
 */
public final class SchedulingRules {

    /** W ostatnich {@value #EXAM_WINDOW_DAYS} dniach przed egzaminem retencja jest co najmniej taka. */
    public static final double EXAM_RETENTION = 0.95;
    public static final int EXAM_WINDOW_DAYS = 30;
    /** Ostatnie dni przed egzaminem są zarezerwowane na same powtórki: nowych pytań się już nie planuje. */
    public static final int EXAM_REVIEW_RESERVE_DAYS = 7;

    private SchedulingRules() {
    }

    /**
     * Retencja użyta do liczenia terminów: wybrana przez użytkownika, a gdy do egzaminu zostało nie więcej niż
     * {@value #EXAM_WINDOW_DAYS} dni, co najmniej {@value #EXAM_RETENTION}. Wyższa retencja skraca odstępy,
     * więc materiał wraca częściej tuż przed egzaminem.
     */
    public static double effectiveRetention(double desired, Long daysToExam) {
        if (daysToExam != null && daysToExam >= 0 && daysToExam <= EXAM_WINDOW_DAYS) {
            return Math.max(desired, EXAM_RETENTION);
        }
        return desired;
    }

    /**
     * Odstęp nie może przekroczyć terminu egzaminu: karta ma wrócić najpóźniej dzień przed nim,
     * a nie za miesiąc po egzaminie. Bez daty egzaminu (albo po nim) odstęp zostaje bez zmian.
     */
    public static double capForExam(double intervalDays, Long daysToExam) {
        if (daysToExam == null || daysToExam < 0) {
            return intervalDays;
        }
        double cap = Math.max(1, daysToExam - 1);
        return Math.min(intervalDays, cap);
    }

    /**
     * Rozrzut terminów, żeby karty uczone tego samego dnia nie wracały wszystkie naraz.
     * Dla odstępów poniżej 2,5 dnia bez zmian. Dalej losujemy całkowitą liczbę dni z przedziału
     * odstęp ± delta, gdzie delta rośnie z długością odstępu (jak w Anki: 15% dla 2,5–7 dni,
     * 10% dla 7–20 dni, 5% powyżej), a wynik nigdy nie jest krótszy niż 2 dni.
     *
     * @param unit liczba z przedziału [0, 1) wylosowana przez wywołującego (przekazana, żeby test był deterministyczny)
     */
    public static double fuzz(double intervalDays, double unit) {
        if (intervalDays < 2.5) {
            return intervalDays;
        }
        double delta = 1.0
                + 0.15 * Math.max(Math.min(intervalDays, 7.0) - 2.5, 0)
                + 0.10 * Math.max(Math.min(intervalDays, 20.0) - 7.0, 0)
                + 0.05 * Math.max(intervalDays - 20.0, 0);
        long min = Math.max(2, Math.round(intervalDays - delta));
        long max = Math.max(min, Math.round(intervalDays + delta));
        double clampedUnit = Math.min(Math.max(unit, 0.0), 0.999_999_999);
        return min + Math.floor(clampedUnit * (max - min + 1));
    }

    /**
     * Ile nowych pytań dziennie, żeby zacząć wszystkie przed egzaminem. Dni liczymy do egzaminu
     * minus rezerwa na same powtórki (co najmniej 1). Zaokrąglenie w górę, 0 gdy nie ma nic nowego.
     */
    public static long suggestedPerDay(long remaining, long daysToExam) {
        if (remaining <= 0) {
            return 0;
        }
        long days = planningDays(daysToExam);
        return (remaining + days - 1) / days;
    }

    public static long planningDays(long daysToExam) {
        return Math.max(1, daysToExam - EXAM_REVIEW_RESERVE_DAYS);
    }
}
