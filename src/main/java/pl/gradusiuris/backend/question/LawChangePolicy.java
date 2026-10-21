package pl.gradusiuris.backend.question;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Reguła „świeżej" zmiany w prawie: pytanie trafia do kategorii „Zmiany w prawie", gdy jego zmiana weszła w życie
 * nie wcześniej niż {@code app.questions.recent-change-days} dni temu (domyślnie 365, czyli do roku) i nie jest jeszcze
 * z przyszłości. Okno liczone jest w chwili zapytania, więc pytanie wypada z kategorii samo, bez żadnej czynności.
 */
@Component
public class LawChangePolicy {

    private final Clock clock;
    private final int recentDays;

    public LawChangePolicy(Clock clock, @Value("${app.questions.recent-change-days:365}") int recentDays) {
        this.clock = clock;
        this.recentDays = Math.max(1, recentDays);
    }

    /** Ile dni wstecz zmiana w prawie uchodzi za świeżą. */
    public int recentDays() {
        return recentDays;
    }

    /** Czy zmiana z tego dnia jest świeża: już obowiązuje i weszła w życie w ostatnich {@link #recentDays()} dniach. */
    public boolean isRecent(LocalDate changedAt) {
        if (changedAt == null) {
            return false;
        }
        LocalDate today = LocalDate.now(clock);
        return !changedAt.isAfter(today) && !changedAt.isBefore(today.minusDays(recentDays));
    }
}
