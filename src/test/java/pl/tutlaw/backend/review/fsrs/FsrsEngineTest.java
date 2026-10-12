package pl.tutlaw.backend.review.fsrs;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FsrsEngineTest {

    private final FsrsEngine engine = new FsrsEngine(FsrsWeights.defaults(), 0.9);

    @Test
    void firstReview_higherRatingShouldProduceHigherStability() {
        double again = engine.firstReview(Rating.AGAIN).stability();
        double hard = engine.firstReview(Rating.HARD).stability();
        double good = engine.firstReview(Rating.GOOD).stability();
        double easy = engine.firstReview(Rating.EASY).stability();

        assertThat(again).isLessThan(hard);
        assertThat(hard).isLessThan(good);
        assertThat(good).isLessThan(easy);
    }

    @Test
    void successfulReview_shouldNeverDecreaseStability() {
        FsrsCardState initial = engine.firstReview(Rating.GOOD);
        FsrsCardState afterGood = engine.nextState(initial, 3, Rating.GOOD);

        assertThat(afterGood.stability()).isGreaterThanOrEqualTo(initial.stability());
    }

    @Test
    void againRating_shouldDropStabilitySignificantly() {
        FsrsCardState initial = engine.firstReview(Rating.GOOD);
        FsrsCardState matured = engine.nextState(initial, 5, Rating.GOOD);
        FsrsCardState afterLapse = engine.nextState(matured, 10, Rating.AGAIN);

        assertThat(afterLapse.stability()).isLessThan(matured.stability());
    }

    @Test
    void nextIntervalDays_shouldGrowWithStability() {
        assertThat(engine.nextIntervalDays(20)).isGreaterThan(engine.nextIntervalDays(2));
    }
}