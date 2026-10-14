package pl.tutlaw.backend.exam;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExamFormatTest {

    @Test
    void theFixedFormatIs150QuestionsAndPassFrom100Points() {
        assertThat(ExamFormat.QUESTION_COUNT).isEqualTo(150);
        assertThat(ExamFormat.PASS_POINTS).isEqualTo(100);
        assertThat(ExamFormat.passPointsFor(150)).isEqualTo(100);
    }

    @Test
    void aShorterExamKeepsTheSameShareOfPoints_roundedUp() {
        assertThat(ExamFormat.passPointsFor(149)).isEqualTo(100); // 99,33 -> 100
        assertThat(ExamFormat.passPointsFor(148)).isEqualTo(99);  // 98,67 -> 99
        assertThat(ExamFormat.passPointsFor(120)).isEqualTo(80);
        assertThat(ExamFormat.passPointsFor(75)).isEqualTo(50);
        assertThat(ExamFormat.passPointsFor(12)).isEqualTo(8);
        assertThat(ExamFormat.passPointsFor(3)).isEqualTo(2);
        assertThat(ExamFormat.passPointsFor(1)).isEqualTo(1);
    }

    @Test
    void theThresholdNeverExceedsTheNumberOfQuestions_andIsAtLeastOne() {
        for (int total = 1; total <= ExamFormat.QUESTION_COUNT; total++) {
            int points = ExamFormat.passPointsFor(total);
            assertThat(points).as("total=%d", total).isBetween(1, total);
        }
    }

    @Test
    void anEmptyExamHasNoThreshold() {
        assertThat(ExamFormat.passPointsFor(0)).isZero();
        assertThat(ExamFormat.passPointsFor(-5)).isZero();
    }
}
