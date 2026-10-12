package pl.tutlaw.backend.review.fsrs;

public class FsrsEngine {

    private final FsrsWeights weights;
    private final double requestRetention;

    public FsrsEngine(FsrsWeights weights, double requestRetention) {
        this.weights = weights;
        this.requestRetention = requestRetention;
    }

    private double w(int i) {
        return weights.w()[i];
    }
    
    public double retrievability(double elapsedDays, double stability) {
        return Math.pow(1 + elapsedDays / (9 * stability), -1);
    }

    public FsrsCardState firstReview(Rating rating) {
        double stability = w(rating.getValue() - 1);
        double difficulty = clampDifficulty(w(4) - w(5) * (rating.getValue() - 3));
        return new FsrsCardState(stability, difficulty);
    }

    public FsrsCardState nextState(FsrsCardState current, double elapsedDays, Rating rating) {
        double r = retrievability(elapsedDays, current.stability());
        double newDifficulty = nextDifficulty(current.difficulty(), rating);

        double newStability = (rating == Rating.AGAIN)
                ? nextForgetStability(newDifficulty, current.stability(), r)
                : nextRecallStability(newDifficulty, current.stability(), r, rating);

        return new FsrsCardState(newStability, newDifficulty);
    }

    public double nextIntervalDays(double stability) {
        return 9 * stability * (1 / requestRetention - 1);
    }

    private double nextDifficulty(double currentDifficulty, Rating rating) {
        double delta = w(6) * (rating.getValue() - 3);
        double updated = currentDifficulty - delta;
        double initialEasyDifficulty = clampDifficulty(w(4) - w(5) * (Rating.EASY.getValue() - 3));
        double reverted = w(7) * initialEasyDifficulty + (1 - w(7)) * updated;
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
        return w(11)
                * Math.pow(difficulty, -w(12))
                * (Math.pow(stability + 1, w(13)) - 1)
                * Math.exp((1 - r) * w(14));
    }

    private double clampDifficulty(double value) {
        return Math.max(1, Math.min(10, value));
    }
}