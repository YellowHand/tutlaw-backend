package pl.tutlaw.backend.review;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LeechPolicyTest {

    @Test
    void card_isDifficult_fromTheThresholdUp() {
        LeechPolicy policy = new LeechPolicy(8);

        assertThat(policy.isLeech(7)).isFalse();
        assertThat(policy.isLeech(8)).isTrue();
        assertThat(policy.isLeech(30)).isTrue();
    }

    @Test
    void newCard_isNeverDifficult() {
        assertThat(new LeechPolicy(8).isLeech(0)).isFalse();
    }

    @Test
    void nonsenseThreshold_isRaisedToOne_soANewCardIsNotDifficultByDefinition() {
        LeechPolicy policy = new LeechPolicy(0);

        assertThat(policy.threshold()).isEqualTo(1);
        assertThat(policy.isLeech(0)).isFalse();
        assertThat(policy.isLeech(1)).isTrue();
    }
}
