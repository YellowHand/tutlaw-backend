package pl.gradusiuris.backend.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Reguły kont bez Springa i bazy, dzięki czemu da się je sprawdzić zwykłymi testami.
 */
final class AuthRules {

    static final int PASSWORD_MIN_LENGTH = 8;

    /** BCrypt używa tylko pierwszych 72 bajtów hasła. Dłuższe odrzucamy, zamiast po cichu je ucinać. */
    static final int PASSWORD_MAX_BYTES = 72;

    private AuthRules() {
    }

    /** Adres e-mail bez spacji z brzegów i małymi literami, żeby "Jan@Gmail.com" i "jan@gmail.com" było jednym kontem. */
    static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /** Null, gdy hasło jest poprawne. W przeciwnym razie komunikat dla użytkownika. */
    static String passwordProblem(String password) {
        if (password == null || password.length() < PASSWORD_MIN_LENGTH) {
            return "Hasło musi mieć co najmniej " + PASSWORD_MIN_LENGTH + " znaków";
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
            return "Hasło jest za długie (maksymalnie około 70 znaków)";
        }
        return null;
    }

    /** Token można wykorzystać, jeśli jeszcze nie był użyty i nie wygasł. */
    static boolean isUsable(LocalDateTime usedAt, LocalDateTime expiresAt, LocalDateTime now) {
        return usedAt == null && expiresAt != null && expiresAt.isAfter(now);
    }

    /** Prawda, gdy poprzednią wiadomość wysłano tak niedawno, że kolejnej jeszcze nie wysyłamy. */
    static boolean withinCooldown(LocalDateTime lastCreatedAt, LocalDateTime now, Duration cooldown) {
        return lastCreatedAt != null && lastCreatedAt.plus(cooldown).isAfter(now);
    }

    /** Krótka podpowiedź, kiedy spróbować ponownie, np. "za mniej niż minutę" albo "za 7 min". */
    static String waitHint(long seconds) {
        if (seconds <= 60) {
            return "za mniej niż minutę";
        }
        return "za " + ((seconds + 59) / 60) + " min";
    }
}
