package pl.tutlaw.backend.auth;

import java.time.Duration;
import java.time.LocalDateTime;

/** Reguły tokenu odświeżającego bez Springa i bazy, żeby dało się je sprawdzić zwykłymi testami. */
final class RefreshRules {

    /**
     * Tyle czasu po wymianie tokenu jego ponowne użycie uznajemy za niewinne: dwie karty odświeżające jednocześnie
     * albo odpowiedź, która zgubiła się w drodze, a przeglądarka ponowiła żądanie. Dłuższe ponowne użycie to kradzież.
     */
    static final Duration REUSE_GRACE = Duration.ofSeconds(10);

    enum Verdict {
        /** Token jest ważny i jeszcze nie był użyty. */
        OK,
        /** Token już wymieniono, ale przed chwilą: wystawiamy kolejny token tej samej rodziny. */
        REUSED_IN_GRACE,
        /** Token już wymieniono dawno temu: ktoś używa kopii. Cała rodzina zostaje unieważniona. */
        REUSED,
        EXPIRED,
        REVOKED
    }

    private RefreshRules() {
    }

    static Verdict judge(LocalDateTime rotatedAt, LocalDateTime revokedAt, LocalDateTime expiresAt, LocalDateTime now) {
        if (revokedAt != null) {
            return Verdict.REVOKED;
        }
        if (expiresAt == null || !expiresAt.isAfter(now)) {
            return Verdict.EXPIRED;
        }
        if (rotatedAt != null) {
            return rotatedAt.plus(REUSE_GRACE).isAfter(now) ? Verdict.REUSED_IN_GRACE : Verdict.REUSED;
        }
        return Verdict.OK;
    }
}
