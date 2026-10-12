package pl.tutlaw.backend.exam;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExamScoringTest {

    @Test
    void scorePercent_isRoundedToOneDecimalPlace() {
        assertThat(ExamScoring.scorePercent(112, 150)).isEqualTo(74.7);
        assertThat(ExamScoring.scorePercent(113, 150)).isEqualTo(75.3);
        assertThat(ExamScoring.scorePercent(1, 3)).isEqualTo(33.3);
        assertThat(ExamScoring.scorePercent(2, 3)).isEqualTo(66.7);
        assertThat(ExamScoring.scorePercent(150, 150)).isEqualTo(100.0);
        assertThat(ExamScoring.scorePercent(0, 150)).isEqualTo(0.0);
    }

    @Test
    void scorePercent_isZeroForAnEmptyExam() {
        assertThat(ExamScoring.scorePercent(0, 0)).isEqualTo(0.0);
    }

    @Test
    void passed_whenTheScoreEqualsTheThreshold() {
        assertThat(ExamScoring.passed(3, 4, 75)).isTrue();
        assertThat(ExamScoring.passed(75, 100, 75)).isTrue();
    }

    @Test
    void passed_usesExactArithmetic_notTheRoundedPercentage() {
        // 112 / 150 = 74,67%: wyświetla się jako 74,7%, ale to nadal mniej niż 75%
        assertThat(ExamScoring.passed(112, 150, 75)).isFalse();
        assertThat(ExamScoring.passed(113, 150, 75)).isTrue();
        // 74,99% wyświetla się jako 75,0%, ale to nadal mniej niż 75%
        assertThat(ExamScoring.scorePercent(7_499, 10_000)).isEqualTo(75.0);
        assertThat(ExamScoring.passed(7_499, 10_000, 75)).isFalse();
    }

    @Test
    void passed_atTheExtremeThresholds() {
        assertThat(ExamScoring.passed(150, 150, 100)).isTrue();
        assertThat(ExamScoring.passed(149, 150, 100)).isFalse();
        assertThat(ExamScoring.passed(1, 200, 1)).isFalse(); // 0,5% < 1%
        assertThat(ExamScoring.passed(2, 200, 1)).isTrue();   // 1% >= 1%
    }

    @Test
    void passed_isNeverTrueForAnEmptyExam() {
        assertThat(ExamScoring.passed(0, 0, 1)).isFalse();
    }
}
