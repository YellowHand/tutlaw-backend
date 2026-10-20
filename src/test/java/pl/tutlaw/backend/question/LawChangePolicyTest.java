package pl.tutlaw.backend.question;

import org.junit.jupiter.api.Test;
import pl.tutlaw.backend.testsupport.MutableClock;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class LawChangePolicyTest {

    // 2026-10-12 12:00 czasu warszawskiego
    private final MutableClock clock = MutableClock.at("2026-10-12T10:00:00Z");

    @Test
    void recent_isTodayBackToExactlyOneYear() {
        LawChangePolicy policy = new LawChangePolicy(clock, 365);

        assertThat(policy.isRecent(LocalDate.of(2026, 10, 12))).isTrue();
        assertThat(policy.isRecent(LocalDate.of(2026, 1, 1))).isTrue();
        assertThat(policy.isRecent(LocalDate.of(2025, 10, 12))).isTrue();   // dokładnie 365 dni temu
        assertThat(policy.isRecent(LocalDate.of(2025, 10, 11))).isFalse();  // 366 dni temu
        assertThat(policy.isRecent(LocalDate.of(2020, 1, 1))).isFalse();
    }

    @Test
    void recent_aChangeFromTheFutureDoesNotApplyYet() {
        LawChangePolicy policy = new LawChangePolicy(clock, 365);

        assertThat(policy.isRecent(LocalDate.of(2026, 10, 13))).isFalse();
        assertThat(policy.isRecent(LocalDate.of(2027, 1, 1))).isFalse();
    }

    @Test
    void recent_withoutADateIsNeverRecent() {
        assertThat(new LawChangePolicy(clock, 365).isRecent(null)).isFalse();
    }

    @Test
    void recent_windowComesFromConfigurationAndIsAtLeastOneDay() {
        LawChangePolicy half = new LawChangePolicy(clock, 180);
        assertThat(half.recentDays()).isEqualTo(180);
        assertThat(half.isRecent(LocalDate.of(2026, 4, 15))).isTrue();   // 180 dni temu
        assertThat(half.isRecent(LocalDate.of(2026, 4, 14))).isFalse();

        LawChangePolicy broken = new LawChangePolicy(clock, 0);
        assertThat(broken.recentDays()).isEqualTo(1);
        assertThat(broken.isRecent(LocalDate.of(2026, 10, 11))).isTrue();
        assertThat(broken.isRecent(LocalDate.of(2026, 10, 10))).isFalse();
    }

    @Test
    void recent_followsTheClock_soAQuestionLeavesTheCategoryByItself() {
        LawChangePolicy policy = new LawChangePolicy(clock, 365);
        LocalDate changedAt = LocalDate.of(2025, 10, 12);
        assertThat(policy.isRecent(changedAt)).isTrue();

        clock.advance(java.time.Duration.ofDays(1));

        assertThat(policy.isRecent(changedAt)).isFalse();
    }
}
