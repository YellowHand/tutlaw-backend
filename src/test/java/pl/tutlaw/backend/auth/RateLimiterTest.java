package pl.tutlaw.backend.auth;

import org.junit.jupiter.api.Test;
import pl.tutlaw.backend.common.exception.TooManyRequestsException;
import pl.tutlaw.backend.testsupport.MutableClock;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterTest {

    private static MutableClock clock() {
        return new MutableClock(Instant.parse("2026-10-12T08:00:00Z"), ZoneId.of("Europe/Warsaw"));
    }

    // ---------------------------------------------------------------- okno przesuwne

    @Test
    void allowsUpToTheLimitThenBlocksUntilTheOldestEventLeavesTheWindow() {
        MutableClock clock = clock();
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(clock);
        Duration window = Duration.ofMinutes(10);

        assertThat(limiter.tryAcquire("k", 3, window)).isTrue();
        clock.advance(Duration.ofMinutes(1));
        assertThat(limiter.tryAcquire("k", 3, window)).isTrue();
        clock.advance(Duration.ofMinutes(1));
        assertThat(limiter.tryAcquire("k", 3, window)).isTrue();
        assertThat(limiter.tryAcquire("k", 3, window)).isFalse();

        // Najstarsze zdarzenie było 2 minuty temu, więc zwolni się za 8 minut.
        assertThat(limiter.retryAfterSeconds("k", window)).isEqualTo(8 * 60L);

        clock.advance(Duration.ofMinutes(8));
        assertThat(limiter.tryAcquire("k", 3, window)).isTrue();
    }

    @Test
    void keysAreIndependent() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(clock());
        Duration window = Duration.ofMinutes(10);

        assertThat(limiter.tryAcquire("a", 1, window)).isTrue();
        assertThat(limiter.tryAcquire("a", 1, window)).isFalse();
        assertThat(limiter.tryAcquire("b", 1, window)).isTrue();
    }

    @Test
    void isLimitedDoesNotRecordAndRecordDoes() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(clock());
        Duration window = Duration.ofMinutes(15);

        for (int i = 0; i < 10; i++) {
            assertThat(limiter.isLimited("fail", 2, window)).isFalse();
        }
        assertThat(limiter.trackedKeys()).isEqualTo(0);

        limiter.record("fail", window);
        assertThat(limiter.isLimited("fail", 2, window)).isFalse();
        limiter.record("fail", window);
        assertThat(limiter.isLimited("fail", 2, window)).isTrue();

        limiter.clear("fail");
        assertThat(limiter.isLimited("fail", 2, window)).isFalse();
    }

    @Test
    void expiredEntriesDisappearFromMemory() {
        MutableClock clock = clock();
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(clock);
        Duration window = Duration.ofMinutes(1);
        limiter.tryAcquire("a", 5, window);

        clock.advance(Duration.ofMinutes(2));

        assertThat(limiter.isLimited("a", 5, window)).isFalse();
        assertThat(limiter.trackedKeys()).isEqualTo(0);
    }

    @Test
    void floodOfUniqueKeysCannotGrowMemoryWithoutBound() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(clock());
        Duration window = Duration.ofHours(1);

        for (int i = 0; i < SlidingWindowRateLimiter.MAX_KEYS + 500; i++) {
            limiter.tryAcquire("ip-" + i, 5, window);
        }

        assertThat(limiter.trackedKeys()).isLessThanOrEqualTo(SlidingWindowRateLimiter.MAX_KEYS);
    }

    // ---------------------------------------------------------------- reguły logowania i maili

    @Test
    void loginIsBlockedAfterEightFailuresForTheSameAccountAndAddressButNotForOthers() {
        AuthRateLimiter limiter = new AuthRateLimiter(clock());

        for (int i = 0; i < AuthRateLimiter.LOGIN_FAILURES; i++) {
            limiter.checkLogin("1.2.3.4", "jan@gmail.com");
            limiter.recordLoginFailure("1.2.3.4", "jan@gmail.com");
        }

        assertThatThrownBy(() -> limiter.checkLogin("1.2.3.4", "JAN@gmail.com "))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("Zbyt wiele prób");
        // Inny adres IP, ten sam użytkownik: nie można w ten sposób zablokować komuś konta z całego świata.
        limiter.checkLogin("5.6.7.8", "jan@gmail.com");
        // Ten sam IP, inne konto.
        limiter.checkLogin("1.2.3.4", "ola@gmail.com");
    }

    @Test
    void successfulLoginResetsFailureCounter() {
        AuthRateLimiter limiter = new AuthRateLimiter(clock());
        for (int i = 0; i < AuthRateLimiter.LOGIN_FAILURES - 1; i++) {
            limiter.recordLoginFailure("1.2.3.4", "jan@gmail.com");
        }

        limiter.recordLoginSuccess("1.2.3.4", "jan@gmail.com");
        for (int i = 0; i < AuthRateLimiter.LOGIN_FAILURES - 1; i++) {
            limiter.recordLoginFailure("1.2.3.4", "jan@gmail.com");
        }

        limiter.checkLogin("1.2.3.4", "jan@gmail.com"); // bez wyjątku
    }

    @Test
    void loginBlockLiftsAfterTheWindowAndTellsHowLongToWait() {
        MutableClock clock = clock();
        AuthRateLimiter limiter = new AuthRateLimiter(clock);
        for (int i = 0; i < AuthRateLimiter.LOGIN_FAILURES; i++) {
            limiter.recordLoginFailure("1.2.3.4", "jan@gmail.com");
        }

        clock.advance(Duration.ofMinutes(5));
        TooManyRequestsException blocked = null;
        try {
            limiter.checkLogin("1.2.3.4", "jan@gmail.com");
        } catch (TooManyRequestsException e) {
            blocked = e;
        }
        assertThat(blocked).isNotNull();
        assertThat(blocked.getRetryAfterSeconds()).isEqualTo(10 * 60L);
        assertThat(blocked.getMessage()).contains("za 10 min");

        clock.advance(Duration.ofMinutes(10));
        limiter.checkLogin("1.2.3.4", "jan@gmail.com");
    }

    @Test
    void oneAddressCannotHammerLoginTooOften() {
        AuthRateLimiter limiter = new AuthRateLimiter(clock());
        for (int i = 0; i < AuthRateLimiter.LOGIN_PER_IP; i++) {
            limiter.checkLogin("1.2.3.4", "konto" + i + "@gmail.com");
        }

        assertThatThrownBy(() -> limiter.checkLogin("1.2.3.4", "jeszcze@gmail.com"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void registrationIsLimitedPerAddress() {
        AuthRateLimiter limiter = new AuthRateLimiter(clock());
        for (int i = 0; i < AuthRateLimiter.REGISTER_PER_IP; i++) {
            limiter.checkRegister("1.2.3.4");
        }

        assertThatThrownBy(() -> limiter.checkRegister("1.2.3.4")).isInstanceOf(TooManyRequestsException.class);
        limiter.checkRegister("5.6.7.8");
    }

    @Test
    void emailActionsAreCappedPerAccountSilentlyAndPerAddressLoudly() {
        AuthRateLimiter limiter = new AuthRateLimiter(clock());

        // Dla jednego konta pierwsze 3 prośby przechodzą, kolejne zwracają false (bez błędu, żeby niczego nie zdradzać).
        assertThat(limiter.allowEmailAction("forgot", "1.1.1.1", "jan@gmail.com")).isTrue();
        assertThat(limiter.allowEmailAction("forgot", "1.1.1.1", "Jan@gmail.com")).isTrue();
        assertThat(limiter.allowEmailAction("forgot", "1.1.1.1", "jan@gmail.com")).isTrue();
        assertThat(limiter.allowEmailAction("forgot", "1.1.1.1", "jan@gmail.com")).isFalse();
        // Inny rodzaj prośby i inne konto liczą się osobno.
        assertThat(limiter.allowEmailAction("resend", "1.1.1.1", "jan@gmail.com")).isTrue();
        assertThat(limiter.allowEmailAction("forgot", "1.1.1.1", "ola@gmail.com")).isTrue();

        // Z jednego adresu IP łącznie jest limit i wtedy odpowiadamy błędem 429.
        AuthRateLimiter other = new AuthRateLimiter(clock());
        for (int i = 0; i < AuthRateLimiter.EMAIL_ACTION_PER_IP; i++) {
            other.allowEmailAction("forgot", "9.9.9.9", "konto" + i + "@gmail.com");
        }
        assertThatThrownBy(() -> other.allowEmailAction("forgot", "9.9.9.9", "kolejne@gmail.com"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void veryLongEmailsDoNotBlowUpTheKeys() {
        AuthRateLimiter limiter = new AuthRateLimiter(clock());
        String huge = "a".repeat(100_000) + "@gmail.com";

        limiter.checkLogin("1.2.3.4", huge);
        limiter.recordLoginFailure("1.2.3.4", huge);
        limiter.checkLogin("1.2.3.4", huge);
    }
}
