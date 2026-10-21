package pl.gradusiuris.backend.auth;

import org.junit.jupiter.api.Test;
import pl.gradusiuris.backend.auth.RefreshRules.Verdict;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshRulesTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 10, 0, 0);
    private static final LocalDateTime LATER = NOW.plusDays(30);

    @Test
    void anUnusedTokenBeforeItsExpiryIsOk() {
        assertThat(RefreshRules.judge(null, null, LATER, NOW)).isEqualTo(Verdict.OK);
    }

    @Test
    void aTokenIsExpiredFromTheExactMomentOfItsExpiry() {
        assertThat(RefreshRules.judge(null, null, NOW, NOW)).isEqualTo(Verdict.EXPIRED);
        assertThat(RefreshRules.judge(null, null, NOW.minusSeconds(1), NOW)).isEqualTo(Verdict.EXPIRED);
        assertThat(RefreshRules.judge(null, null, null, NOW)).isEqualTo(Verdict.EXPIRED);
    }

    @Test
    void aRevokedTokenIsRevokedEvenIfItWouldOtherwiseBeOk() {
        assertThat(RefreshRules.judge(null, NOW.minusMinutes(1), LATER, NOW)).isEqualTo(Verdict.REVOKED);
        assertThat(RefreshRules.judge(NOW.minusMinutes(5), NOW.minusMinutes(1), LATER, NOW)).isEqualTo(Verdict.REVOKED);
    }

    @Test
    void aTokenUsedJustNowIsAHarmlessRetry() {
        assertThat(RefreshRules.judge(NOW.minusSeconds(3), null, LATER, NOW)).isEqualTo(Verdict.REUSED_IN_GRACE);
        assertThat(RefreshRules.judge(NOW.minusSeconds(9), null, LATER, NOW)).isEqualTo(Verdict.REUSED_IN_GRACE);
    }

    @Test
    void aTokenUsedLongAgoIsTreatedAsTheft() {
        assertThat(RefreshRules.judge(NOW.minusSeconds(10), null, LATER, NOW)).isEqualTo(Verdict.REUSED);
        assertThat(RefreshRules.judge(NOW.minusDays(2), null, LATER, NOW)).isEqualTo(Verdict.REUSED);
    }

    @Test
    void anOldUsedTokenThatAlsoExpiredIsJustExpired_noFamilyRevocation() {
        assertThat(RefreshRules.judge(NOW.minusDays(40), null, NOW.minusDays(10), NOW)).isEqualTo(Verdict.EXPIRED);
    }
}
