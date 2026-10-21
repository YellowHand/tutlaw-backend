package pl.gradusiuris.backend.review;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerTimesTest {

    @Test
    void missingAndNegativeTimesCountAsZero() {
        assertThat(AnswerTimes.capped(null)).isZero();
        assertThat(AnswerTimes.capped(-3.0)).isZero();
    }

    @Test
    void normalTimesPassThrough() {
        assertThat(AnswerTimes.capped(0.0)).isZero();
        assertThat(AnswerTimes.capped(14.5)).isEqualTo(14.5);
        assertThat(AnswerTimes.capped(120.0)).isEqualTo(120.0);
    }

    @Test
    void longTimesAreCappedAtTwoMinutes() {
        assertThat(AnswerTimes.capped(121.0)).isEqualTo(120.0);
        assertThat(AnswerTimes.capped(3600.0)).isEqualTo(120.0);
    }
}
