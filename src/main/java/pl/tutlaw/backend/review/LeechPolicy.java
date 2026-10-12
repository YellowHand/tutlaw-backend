package pl.tutlaw.backend.review;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * „Trudne pytanie" (ang. leech): karta, na której użytkownik pomylił się już wiele razy. Dalsze powtarzanie
 * jej w tej samej formie rzadko pomaga, więc po przekroczeniu progu aplikacja podpowiada, żeby ją zawiesić
 * albo zgłosić (może być źle sformułowana). Niczego nie zawiesza sama.
 */
@Component
public class LeechPolicy {

    private final int threshold;

    public LeechPolicy(@Value("${app.review.leech-threshold:8}") int threshold) {
        this.threshold = Math.max(1, threshold);
    }

    /** Od ilu błędów (lapses) karta uchodzi za trudną. */
    public int threshold() {
        return threshold;
    }

    public boolean isLeech(int lapses) {
        return lapses >= threshold;
    }
}
