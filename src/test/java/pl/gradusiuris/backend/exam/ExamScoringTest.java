package pl.gradusiuris.backend.exam;

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
    void passed_fullExam_needs100Points() {
        assertThat(ExamScoring.passed(100, 150)).isTrue();
        assertThat(ExamScoring.passed(99, 150)).isFalse();
        assertThat(ExamScoring.passed(150, 150)).isTrue();
        assertThat(ExamScoring.passed(0, 150)).isFalse();
    }

    @Test
    void passed_usesPointsNotTheRoundedPercentage() {
        // 100 / 150 = 66,67%: wyświetla się jako 66,7%, a zaliczenie zaczyna się dokładnie od 100 punktów
        assertThat(ExamScoring.scorePercent(100, 150)).isEqualTo(66.7);
        assertThat(ExamScoring.scorePercent(99, 150)).isEqualTo(66.0);
        assertThat(ExamScoring.passed(99, 150)).isFalse();
        assertThat(ExamScoring.passed(100, 150)).isTrue();
    }

    @Test
    void passed_aShorterExamUsesTheProportionalThreshold() {
        assertThat(ExamScoring.passed(80, 120)).isTrue();
        assertThat(ExamScoring.passed(79, 120)).isFalse();
        assertThat(ExamScoring.passed(2, 3)).isTrue();
        assertThat(ExamScoring.passed(1, 3)).isFalse();
    }

    @Test
    void passed_isNeverTrueForAnEmptyExam() {
        assertThat(ExamScoring.passed(0, 0)).isFalse();
    }

    @Test
    void thresholdPercent_roundsThePointsThresholdUp() {
        assertThat(ExamScoring.thresholdPercent(100, 150)).isEqualTo(67);
        assertThat(ExamScoring.thresholdPercent(8, 12)).isEqualTo(67);
        assertThat(ExamScoring.thresholdPercent(1, 1)).isEqualTo(100);
        assertThat(ExamScoring.thresholdPercent(50, 75)).isEqualTo(67);
    }

    @Test
    void thresholdPercent_staysWithinTheDatabaseRange() {
        for (int total = 1; total <= ExamFormat.QUESTION_COUNT; total++) {
            int percent = ExamScoring.thresholdPercent(ExamFormat.passPointsFor(total), total);
            assertThat(percent).as("total=%d", total).isBetween(1, 100);
        }
        assertThat(ExamScoring.thresholdPercent(0, 0)).isEqualTo(100);
    }
}
