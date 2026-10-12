package pl.tutlaw.backend.review.fsrs;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

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

    @Test
    void firstGood_shouldStartWithLowDifficulty_andOneAgainShouldNotPinItToTheMaximum() {
        FsrsCardState first = engine.firstReview(Rating.GOOD);
        FsrsCardState afterLapse = engine.nextState(first, 3, Rating.AGAIN);

        assertThat(first.difficulty()).isCloseTo(2.1176, within(0.001));
        assertThat(afterLapse.difficulty()).isLessThan(9.0);
    }

    @Test
    void weightsOfWrongLength_shouldBeRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new FsrsEngine(new FsrsWeights(new double[17]), 0.9));
    }

    /**
     * Wartości wzorcowe wygenerowane biblioteką py-fsrs 6.3.2 (Scheduler bez kroków nauki i bez losowania),
     * kolejne wiersze: {godziny od poprzedniej powtórki, ocena 1–4, stabilność, trudność}.
     */
    @Nested
    class MatchesReferenceImplementation {

        private static final double TOLERANCE = 1e-9;

        private void replay(double[][] steps) {
            FsrsCardState state = null;
            for (double[] step : steps) {
                Rating rating = Rating.values()[(int) step[1] - 1];
                state = state == null
                        ? engine.firstReview(rating)
                        : engine.nextState(state, step[0] / 24.0, rating);
                assertThat(state.stability()).as("stabilność po ocenie %s", (int) step[1])
                        .isCloseTo(step[2], within(TOLERANCE));
                assertThat(state.difficulty()).as("trudność po ocenie %s", (int) step[1])
                        .isCloseTo(step[3], within(TOLERANCE));
            }
        }

        @Test
        void goodX6OnSchedule() {
            replay(new double[][]{
                    {0, 3, 2.3065, 2.118103970459016},
                    {48, 3, 10.964332335820698, 2.111214235785395},
                    {168, 3, 36.123635746675305, 2.1043313908464483},
                    {480, 3, 96.85114584645595, 2.0974554287524403},
                    {1200, 3, 227.4933392943264, 2.0905863426205262},
                    {3000, 3, 507.6716077238869, 2.083724125574744}
            });
        }

        @Test
        void lapseAfterTwoGoods() {
            replay(new double[][]{
                    {0, 3, 2.3065, 2.118103970459016},
                    {72, 3, 13.826903694354568, 2.111214235785395},
                    {240, 1, 1.6661407475790257, 7.392238132342694},
                    {24, 3, 3.6890352371468422, 7.38007426350719},
                    {72, 3, 8.664980070318027, 7.3679225585405215},
                    {240, 3, 21.751496868828998, 7.35578300527882}
            });
        }

        @Test
        void hardThenGoods() {
            replay(new double[][]{
                    {0, 3, 2.3065, 2.118103970459016},
                    {72, 3, 13.826903694354568, 2.111214235785395},
                    {240, 2, 34.049056896875726, 4.748284761594571},
                    {120, 3, 46.71704832216717, 4.738764846129815},
                    {480, 3, 89.58609651701275, 4.729254450580523},
                    {1440, 3, 195.6820278080164, 4.719753565426781}
            });
        }

        @Test
        void firstAgainThenGoods() {
            replay(new double[][]{
                    {0, 1, 0.212, 6.4133},
                    {24, 3, 1.8867876195204154, 6.402115069296838},
                    {72, 3, 7.638230927412736, 6.3909413235243795},
                    {240, 3, 23.926564056353264, 6.379778751497693},
                    {720, 3, 65.00109033187397, 6.368627342043034}
            });
        }

        @Test
        void firstEasyThenHardEasy() {
            replay(new double[][]{
                    {0, 4, 8.2956, 1.0},
                    {192, 2, 26.704183359371363, 4.010608969296839},
                    {120, 4, 53.83241029097045, 1.994461961754711},
                    {960, 3, 161.79558013601252, 1.9876958690897946}
            });
        }

        @Test
        void sameDayRepeat() {
            replay(new double[][]{
                    {0, 1, 0.212, 6.4133},
                    {3, 1, 0.08335671711031604, 8.806304468856837},
                    {3, 3, 0.10314065007785231, 8.792726533684817},
                    {24, 3, 0.6952546986897922, 8.77916217644797},
                    {96, 3, 3.0768787858552797, 8.76561138356836}
            });
        }

        @Test
        void sameDayAfterReview() {
            replay(new double[][]{
                    {0, 3, 2.3065, 2.118103970459016},
                    {72, 3, 13.826903694354568, 2.111214235785395},
                    {2, 1, 4.129931781015444, 7.392238132342694},
                    {5, 3, 4.129931781015444, 7.38007426350719},
                    {48, 3, 7.7660299360783664, 7.3679225585405215}
            });
        }

        @Test
        void overdueReview() {
            replay(new double[][]{
                    {0, 3, 2.3065, 2.118103970459016},
                    {48, 3, 10.964332335820698, 2.111214235785395},
                    {720, 3, 77.29502132804201, 2.1043313908464483},
                    {2160, 1, 3.652079083302019, 7.389975788014609},
                    {48, 3, 7.269911632112038, 7.377814181523433}
            });
        }

        @Test
        void retrievabilityMatchesReference() {
            double[][] expected = {
            {0, 5.0, 1.0},
            {5, 5.0, 0.9},
            {15, 5.0, 0.8093881035731708},
            {50, 5.0, 0.6928266345726217},
            {3, 0.5, 0.742717453487897}
            };
            for (double[] row : expected) {
                assertThat(engine.retrievability(row[0], row[1])).isCloseTo(row[2], within(TOLERANCE));
            }
        }

        @Test
        void intervalsMatchReferenceAfterRoundingToFullDays() {
            double[][] expected = {
            {0.212, 1},
            {1.2931, 1},
            {2.3065, 2},
            {8.2956, 8},
            {30.0, 30},
            {400.0, 400}
            };
            for (double[] row : expected) {
                long days = Math.max(1, Math.round(engine.nextIntervalDays(row[0])));
                assertThat(days).isEqualTo((long) row[1]);
            }
        }
    }
}
