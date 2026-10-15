package pl.tutlaw.backend.auth;

import org.springframework.stereotype.Component;
import pl.tutlaw.backend.common.exception.TooManyRequestsException;

import java.time.Clock;
import java.time.Duration;

/**
 * Limity prób na endpointach /api/auth (logowanie, rejestracja, reset hasła, ponowne wysłanie maila).
 * <p>
 * Adres IP bierzemy z {@code request.getRemoteAddr()}. Za serwerem pośredniczącym (nginx, hosting)
 * trzeba ustawić {@code server.forward-headers-strategy: native}, bo inaczej wszyscy mają ten sam adres.
 */
@Component
public class AuthRateLimiter {

    static final int LOGIN_PER_IP = 30;
    static final Duration LOGIN_PER_IP_WINDOW = Duration.ofMinutes(5);

    /** Nieudane logowania na to samo konto z tego samego adresu IP. */
    static final int LOGIN_FAILURES = 8;
    static final Duration LOGIN_FAILURES_WINDOW = Duration.ofMinutes(15);

    static final int REGISTER_PER_IP = 10;
    static final Duration REGISTER_WINDOW = Duration.ofHours(1);

    static final int EMAIL_ACTION_PER_IP = 10;
    static final int EMAIL_ACTION_PER_ACCOUNT = 3;
    static final Duration EMAIL_ACTION_WINDOW = Duration.ofHours(1);

    static final int RESET_ATTEMPTS_PER_IP = 20;
    static final Duration RESET_ATTEMPTS_WINDOW = Duration.ofHours(1);

    /**
     * Odświeżanie i wylogowanie. Limit jest hojny, bo wiele osób siedzi za jednym adresem IP (sieć uczelni), a sam
     * token jest nie do zgadnięcia: limit tylko ucina nadużycia.
     */
    static final int REFRESH_PER_IP = 300;
    static final Duration REFRESH_WINDOW = Duration.ofMinutes(5);

    private static final int MAX_EMAIL_KEY_LENGTH = 254;

    private final SlidingWindowRateLimiter limiter;

    public AuthRateLimiter(Clock clock) {
        this.limiter = new SlidingWindowRateLimiter(clock);
    }

    /** Wywołujemy przed sprawdzaniem hasła. Rzuca 429, gdy IP robi za dużo prób albo konto jest zablokowane na chwilę. */
    public void checkLogin(String ip, String email) {
        String ipKey = "login-ip|" + ip;
        if (!limiter.tryAcquire(ipKey, LOGIN_PER_IP, LOGIN_PER_IP_WINDOW)) {
            throw tooMany(ipKey, LOGIN_PER_IP_WINDOW);
        }
        String failKey = failureKey(ip, email);
        if (limiter.isLimited(failKey, LOGIN_FAILURES, LOGIN_FAILURES_WINDOW)) {
            throw tooMany(failKey, LOGIN_FAILURES_WINDOW);
        }
    }

    public void recordLoginFailure(String ip, String email) {
        limiter.record(failureKey(ip, email), LOGIN_FAILURES_WINDOW);
    }

    public void recordLoginSuccess(String ip, String email) {
        limiter.clear(failureKey(ip, email));
    }

    public void checkRegister(String ip) {
        String key = "register-ip|" + ip;
        if (!limiter.tryAcquire(key, REGISTER_PER_IP, REGISTER_WINDOW)) {
            throw tooMany(key, REGISTER_WINDOW);
        }
    }

    public void checkRefresh(String ip) {
        String key = "refresh-ip|" + ip;
        if (!limiter.tryAcquire(key, REFRESH_PER_IP, REFRESH_WINDOW)) {
            throw tooMany(key, REFRESH_WINDOW);
        }
    }

    /** Ustawianie nowego hasła z tokenem z maila. Sam token jest nie do zgadnięcia, limit tylko ogranicza nadużycia. */
    public void checkResetAttempt(String ip) {
        String key = "reset-ip|" + ip;
        if (!limiter.tryAcquire(key, RESET_ATTEMPTS_PER_IP, RESET_ATTEMPTS_WINDOW)) {
            throw tooMany(key, RESET_ATTEMPTS_WINDOW);
        }
    }

    /**
     * Prośba o wysłanie maila ("zapomniałem hasła", "wyślij link ponownie").
     * Nadmiar z jednego IP kończy się błędem 429. Nadmiar dla jednego konta zwraca false, a wywołujący
     * ma wtedy odpowiedzieć tak samo jak zawsze i niczego nie wysyłać (inaczej odpowiedź zdradzałaby, że konto istnieje).
     */
    public boolean allowEmailAction(String action, String ip, String email) {
        String ipKey = action + "-ip|" + ip;
        if (!limiter.tryAcquire(ipKey, EMAIL_ACTION_PER_IP, EMAIL_ACTION_WINDOW)) {
            throw tooMany(ipKey, EMAIL_ACTION_WINDOW);
        }
        return limiter.tryAcquire(action + "-account|" + emailKey(email), EMAIL_ACTION_PER_ACCOUNT, EMAIL_ACTION_WINDOW);
    }

    private static String failureKey(String ip, String email) {
        return "login-fail|" + ip + "|" + emailKey(email);
    }

    private static String emailKey(String email) {
        String normalized = AuthRules.normalizeEmail(email);
        return normalized.length() > MAX_EMAIL_KEY_LENGTH ? normalized.substring(0, MAX_EMAIL_KEY_LENGTH) : normalized;
    }

    private TooManyRequestsException tooMany(String key, Duration window) {
        long seconds = limiter.retryAfterSeconds(key, window);
        return new TooManyRequestsException(
                "Zbyt wiele prób. Spróbuj ponownie " + AuthRules.waitHint(seconds), seconds);
    }
}
