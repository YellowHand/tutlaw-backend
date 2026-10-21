package pl.gradusiuris.backend.auth;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRulesTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 10, 0);

    @Test
    void normalizeEmailTrimsAndLowercases() {
        assertThat(AuthRules.normalizeEmail("  Jan.Kowalski@GMAIL.com ")).isEqualTo("jan.kowalski@gmail.com");
        assertThat(AuthRules.normalizeEmail(null)).isEqualTo("");
    }

    @Test
    void passwordMustHaveAtLeastEightCharacters() {
        assertThat(AuthRules.passwordProblem("1234567")).isNotNull();
        assertThat(AuthRules.passwordProblem(null)).isNotNull();
        assertThat(AuthRules.passwordProblem("12345678")).isNull();
    }

    @Test
    void passwordLimitIsInBytesBecausePolishLettersTakeTwo() {
        assertThat(AuthRules.passwordProblem("a".repeat(72))).isNull();
        assertThat(AuthRules.passwordProblem("a".repeat(73))).isNotNull();
        assertThat(AuthRules.passwordProblem("ł".repeat(36))).isNull();
        assertThat(AuthRules.passwordProblem("ł".repeat(37))).isNotNull();
    }

    @Test
    void tokenIsUsableOnlyWhenUnusedAndNotExpired() {
        LocalDateTime later = NOW.plusMinutes(5);
        assertThat(AuthRules.isUsable(null, later, NOW)).isTrue();
        assertThat(AuthRules.isUsable(NOW.minusMinutes(1), later, NOW)).isFalse();
        assertThat(AuthRules.isUsable(null, NOW.minusSeconds(1), NOW)).isFalse();
        assertThat(AuthRules.isUsable(null, NOW, NOW)).isFalse();
        assertThat(AuthRules.isUsable(null, null, NOW)).isFalse();
    }

    @Test
    void cooldownCoversTheWholeIntervalAfterLastMail() {
        Duration cooldown = Duration.ofMinutes(2);
        assertThat(AuthRules.withinCooldown(null, NOW, cooldown)).isFalse();
        assertThat(AuthRules.withinCooldown(NOW.minusSeconds(30), NOW, cooldown)).isTrue();
        assertThat(AuthRules.withinCooldown(NOW.minusMinutes(2), NOW, cooldown)).isFalse();
        assertThat(AuthRules.withinCooldown(NOW.minusMinutes(5), NOW, cooldown)).isFalse();
    }

    @Test
    void waitHintRoundsUpToWholeMinutes() {
        assertThat(AuthRules.waitHint(1)).isEqualTo("za mniej niż minutę");
        assertThat(AuthRules.waitHint(60)).isEqualTo("za mniej niż minutę");
        assertThat(AuthRules.waitHint(61)).isEqualTo("za 2 min");
        assertThat(AuthRules.waitHint(600)).isEqualTo("za 10 min");
        assertThat(AuthRules.waitHint(3600)).isEqualTo("za 60 min");
    }
}
