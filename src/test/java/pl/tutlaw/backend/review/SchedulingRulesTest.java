package pl.tutlaw.backend.review;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulingRulesTest {

    // ---------------------------------------------------------------- retencja w trybie egzaminacyjnym

    @Test
    void retention_withoutExam_staysAsChosen() {
        assertThat(SchedulingRules.effectiveRetention(0.90, null)).isEqualTo(0.90);
    }

    @Test
    void retention_farFromExam_staysAsChosen() {
        assertThat(SchedulingRules.effectiveRetention(0.90, 31L)).isEqualTo(0.90);
        assertThat(SchedulingRules.effectiveRetention(0.90, 200L)).isEqualTo(0.90);
    }

    @Test
    void retention_inLast30Days_isRaisedToAtLeast95() {
        assertThat(SchedulingRules.effectiveRetention(0.90, 30L)).isEqualTo(SchedulingRules.EXAM_RETENTION);
        assertThat(SchedulingRules.effectiveRetention(0.85, 3L)).isEqualTo(SchedulingRules.EXAM_RETENTION);
        assertThat(SchedulingRules.effectiveRetention(0.90, 0L)).isEqualTo(SchedulingRules.EXAM_RETENTION);
    }

    @Test
    void retention_chosenHigherThanExamLevel_isNotLowered() {
        assertThat(SchedulingRules.effectiveRetention(0.97, 10L)).isEqualTo(0.97);
    }

    // ---------------------------------------------------------------- przycięcie do egzaminu

    @Test
    void cap_withoutExamOrAfterIt_changesNothing() {
        assertThat(SchedulingRules.capForExam(120, null)).isEqualTo(120);
        assertThat(SchedulingRules.capForExam(120, -3L)).isEqualTo(120);
    }

    @Test
    void cap_cardComesBackTheDayBeforeTheExam() {
        assertThat(SchedulingRules.capForExam(60, 10L)).isEqualTo(9);
        assertThat(SchedulingRules.capForExam(5, 10L)).isEqualTo(5);
    }

    @Test
    void cap_neverBelowOneDay_evenOnTheExamDay() {
        assertThat(SchedulingRules.capForExam(30, 1L)).isEqualTo(1);
        assertThat(SchedulingRules.capForExam(30, 0L)).isEqualTo(1);
    }

    @Test
    void cap_doesNotLengthenShortIntervals() {
        assertThat(SchedulingRules.capForExam(0.2, 10L)).isEqualTo(0.2);
    }

    // ---------------------------------------------------------------- fuzz

    @Test
    void fuzz_leavesShortIntervalsAlone() {
        assertThat(SchedulingRules.fuzz(0.2, 0.5)).isEqualTo(0.2);
        assertThat(SchedulingRules.fuzz(1.0, 0.99)).isEqualTo(1.0);
        assertThat(SchedulingRules.fuzz(2.4, 0.0)).isEqualTo(2.4);
    }

    @Test
    void fuzz_staysInsideTheRange_andIsWholeDays() {
        Random random = new Random(7);
        for (double interval : new double[]{2.5, 3, 5, 7, 10, 20, 40, 120, 365}) {
            double delta = 1.0
                    + 0.15 * Math.max(Math.min(interval, 7.0) - 2.5, 0)
                    + 0.10 * Math.max(Math.min(interval, 20.0) - 7.0, 0)
                    + 0.05 * Math.max(interval - 20.0, 0);
            for (int i = 0; i < 200; i++) {
                double result = SchedulingRules.fuzz(interval, random.nextDouble());
                assertThat(result).isBetween(Math.max(2.0, Math.floor(interval - delta)), Math.ceil(interval + delta));
                assertThat(result).isEqualTo(Math.rint(result));
            }
        }
    }

    @Test
    void fuzz_neverShorterThanTwoDays() {
        for (int i = 0; i < 100; i++) {
            assertThat(SchedulingRules.fuzz(2.5, i / 100.0)).isGreaterThanOrEqualTo(2.0);
        }
    }

    @Test
    void fuzz_actuallySpreadsTheDates() {
        Random random = new Random(11);
        java.util.Set<Double> seen = new java.util.HashSet<>();
        for (int i = 0; i < 200; i++) {
            seen.add(SchedulingRules.fuzz(30, random.nextDouble()));
        }
        // 30 dni: delta = 1 + 0,15*4,5 + 0,10*13 + 0,05*10 = 3,475, więc kilka różnych terminów.
        assertThat(seen.size()).isGreaterThanOrEqualTo(5);
    }

    @Test
    void fuzz_edgeUnitValuesAreSafe() {
        assertThat(SchedulingRules.fuzz(30, 0.0)).isLessThanOrEqualTo(30);
        assertThat(SchedulingRules.fuzz(30, 1.0)).isGreaterThanOrEqualTo(30);
        assertThat(SchedulingRules.fuzz(30, -1.0)).isEqualTo(SchedulingRules.fuzz(30, 0.0));
    }

    @Test
    void fuzzer_isDeterministicForAFixedGenerator() {
        IntervalFuzzer a = new IntervalFuzzer(new Random(5));
        IntervalFuzzer b = new IntervalFuzzer(new Random(5));

        assertThat(a.apply(30)).isEqualTo(b.apply(30));
    }

    // ---------------------------------------------------------------- plan nowych pytań

    @Test
    void suggestedPerDay_dividesRemainingByDaysMinusReserve() {
        // 44 dni - 14 rezerwy = 30 dni planowania; 90 pytań => 3 dziennie
        assertThat(SchedulingRules.planningDays(44)).isEqualTo(30);
        assertThat(SchedulingRules.suggestedPerDay(90, 44)).isEqualTo(3);
    }

    @Test
    void suggestedPerDay_roundsUp() {
        assertThat(SchedulingRules.suggestedPerDay(91, 44)).isEqualTo(4);
    }

    @Test
    void suggestedPerDay_nothingLeft_isZero() {
        assertThat(SchedulingRules.suggestedPerDay(0, 44)).isEqualTo(0);
    }

    @Test
    void suggestedPerDay_insideTheReserve_planningStops() {
        // w ostatnich 14 dniach nie planujemy nowych pytań — zostają same powtórki
        assertThat(SchedulingRules.planningDays(14)).isEqualTo(0);
        assertThat(SchedulingRules.planningDays(5)).isEqualTo(0);
        assertThat(SchedulingRules.planningDays(0)).isEqualTo(0);
        assertThat(SchedulingRules.suggestedPerDay(40, 14)).isEqualTo(0);
        assertThat(SchedulingRules.suggestedPerDay(40, 5)).isEqualTo(0);
        assertThat(SchedulingRules.suggestedPerDay(40, 0)).isEqualTo(0);
    }

    @Test
    void suggestedPerDay_firstPlanningDay_usesTheWholeRemainder() {
        // 15 dni do egzaminu = 1 dzień planowania: wszystko co zostało trzeba zacząć dziś
        assertThat(SchedulingRules.planningDays(15)).isEqualTo(1);
        assertThat(SchedulingRules.suggestedPerDay(40, 15)).isEqualTo(40);
    }
}
