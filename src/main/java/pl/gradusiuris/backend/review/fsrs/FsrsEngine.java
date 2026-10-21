package pl.gradusiuris.backend.review.fsrs;

/**
 * Implementacja algorytmu FSRS-6 (21 parametrów, w0–w20).
 *
 * <ul>
 *   <li>Krzywa zapominania ma kształt zależny od w20: R(t, S) = (1 + factor * t / S)^(-w20),
 *       gdzie factor = 0,9^(-1/w20) - 1, więc R(S, S) = 90%.</li>
 *   <li>Trudność: D0(G) = w4 - e^(w5 * (G - 1)) + 1, aktualizacja z liniowym tłumieniem
 *       i powrotem do średniej (w7).</li>
 *   <li>Powtórka w tej samej dobie (mniej niż 1 dzień od poprzedniej) używa osobnego wzoru
 *       na stabilność (w17–w19).</li>
 * </ul>
 *
 * Wartości są sprawdzane testem względem referencyjnej biblioteki py-fsrs
 * (patrz FsrsEngineTest), a stabilność po powtórce liczy się z trudnością <b>sprzed</b>
 * aktualizacji, tak jak w specyfikacji.
 */
public class FsrsEngine {

    public static final double STABILITY_MIN = 0.001;
    public static final double MAX_INTERVAL_DAYS = 36_500;

    private static final int PARAMETER_COUNT = 21;
    private static final double MIN_DIFFICULTY = 1.0;
    private static final double MAX_DIFFICULTY = 10.0;

    private final FsrsWeights weights;
    private final double requestRetention;
    /** Wykładnik krzywej zapominania (ujemny): -w20. */
    private final double decay;
    private final double factor;

    public FsrsEngine(FsrsWeights weights, double requestRetention) {
        if (weights.w().length != PARAMETER_COUNT) {
            throw new IllegalArgumentException(
                    "FSRS-6 wymaga " + PARAMETER_COUNT + " parametrów, podano " + weights.w().length);
        }
        this.weights = weights;
        this.requestRetention = requestRetention;
        this.decay = -weights.w()[20];
        this.factor = Math.pow(0.9, 1.0 / decay) - 1.0;
    }

    private double w(int i) {
        return weights.w()[i];
    }

    /** Szansa przypomnienia sobie karty po {@code elapsedDays} dniach od ostatniej powtórki. */
    public double retrievability(double elapsedDays, double stability) {
        if (stability <= 0) return 0;
        return Math.pow(1 + factor * Math.max(0, elapsedDays) / stability, decay);
    }

    public FsrsCardState firstReview(Rating rating) {
        double stability = clampStability(w(rating.getValue() - 1));
        double difficulty = clampDifficulty(initialDifficulty(rating));
        return new FsrsCardState(stability, difficulty);
    }

    public FsrsCardState nextState(FsrsCardState current, double elapsedDays, Rating rating) {
        double stability = current.stability();
        double difficulty = current.difficulty();

        // Stabilność liczymy z trudnością sprzed tej oceny, trudność aktualizujemy osobno.
        double newStability;
        if (elapsedDays < 1.0) {
            newStability = shortTermStability(stability, rating);
        } else {
            double r = retrievability(elapsedDays, stability);
            newStability = rating == Rating.AGAIN
                    ? nextForgetStability(difficulty, stability, r)
                    : nextRecallStability(difficulty, stability, r, rating);
        }

        return new FsrsCardState(clampStability(newStability), nextDifficulty(difficulty, rating));
    }

    /** Odstęp (w dniach) do chwili, gdy szansa przypomnienia spadnie do domyślnej retencji z konfiguracji. */
    public double nextIntervalDays(double stability) {
        return nextIntervalDays(stability, requestRetention);
    }

    /** To samo dla retencji wybranej przez użytkownika (albo podniesionej w trybie egzaminacyjnym). */
    public double nextIntervalDays(double stability, double retention) {
        double interval = (stability / factor) * (Math.pow(retention, 1.0 / decay) - 1.0);
        return Math.min(Math.max(interval, 0), MAX_INTERVAL_DAYS);
    }

    private double initialDifficulty(Rating rating) {
        return w(4) - Math.exp(w(5) * (rating.getValue() - 1)) + 1;
    }

    private double nextDifficulty(double difficulty, Rating rating) {
        double delta = -(w(6) * (rating.getValue() - 3));
        double damped = difficulty + (10.0 - difficulty) * delta / 9.0;
        // Powrót do średniej w kierunku D0(4), bez przycinania, jak w referencji.
        double reverted = w(7) * initialDifficulty(Rating.EASY) + (1 - w(7)) * damped;
        return clampDifficulty(reverted);
    }

    private double nextRecallStability(double difficulty, double stability, double r, Rating rating) {
        double hardPenalty = rating == Rating.HARD ? w(15) : 1.0;
        double easyBonus = rating == Rating.EASY ? w(16) : 1.0;

        return stability * (
                1 + Math.exp(w(8))
                        * (11 - difficulty)
                        * Math.pow(stability, -w(9))
                        * (Math.exp((1 - r) * w(10)) - 1)
                        * hardPenalty
                        * easyBonus
        );
    }

    private double nextForgetStability(double difficulty, double stability, double r) {
        double longTerm = w(11)
                * Math.pow(difficulty, -w(12))
                * (Math.pow(stability + 1, w(13)) - 1)
                * Math.exp((1 - r) * w(14));
        double shortTerm = stability / Math.exp(w(17) * w(18));
        return Math.min(longTerm, shortTerm);
    }

    private double shortTermStability(double stability, Rating rating) {
        double increase = Math.exp(w(17) * (rating.getValue() - 3 + w(18))) * Math.pow(stability, -w(19));
        if (rating != Rating.AGAIN) {
            increase = Math.max(increase, 1.0);
        }
        return stability * increase;
    }

    private double clampStability(double value) {
        return Math.max(value, STABILITY_MIN);
    }

    private double clampDifficulty(double value) {
        return Math.max(MIN_DIFFICULTY, Math.min(MAX_DIFFICULTY, value));
    }
}
