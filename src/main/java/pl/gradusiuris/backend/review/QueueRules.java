package pl.gradusiuris.backend.review;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Reguły układania kolejki powtórek, bez bazy i bez Springa.
 * Zaległe pytania idą od tych, które użytkownik najprawdopodobniej już zapomniał, a dzienny limit
 * chroni przed ścianą zaległości po przerwie w nauce.
 */
final class QueueRules {

    /** Największa jednorazowa porcja ponad limit ("Powtórz jeszcze"), żeby jedno żądanie nie ładowało całej bazy. */
    static final int MAX_EXTRA = 200;

    private QueueRules() {
    }

    /** Zaległe pytanie: termin już minął, a {@code retrievability} to szansa przypomnienia (0-1, im mniej, tym pilniej). */
    record DueItem(Long questionId, LocalDateTime dueDate, double retrievability) {
    }

    /** Od najmniejszej szansy przypomnienia; przy remisie starszy termin, potem mniejsze id (stała kolejność). */
    static List<DueItem> prioritize(List<DueItem> due) {
        return due.stream()
                .sorted(Comparator
                        .comparingDouble(DueItem::retrievability)
                        .thenComparing(DueItem::dueDate)
                        .thenComparing(DueItem::questionId))
                .toList();
    }

    /**
     * Ile zaległych pytań można jeszcze dziś dać do nauki. Bez {@code extraReviews}: dzienny limit minus to, co już
     * zrobiono. Z nim ("Powtórz jeszcze N"): dokładnie tyle, niezależnie od limitu.
     */
    static int remainingAllowance(int dailyLimit, long doneToday, Integer extraReviews) {
        if (extraReviews != null) {
            return clampExtra(extraReviews);
        }
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, dailyLimit - doneToday));
    }

    /** Ile zaległych pytań nie zmieści się w tej sesji i poczeka na kolejne dni. */
    static long deferred(long dueNow, int remainingAllowance) {
        return Math.max(0, dueNow - remainingAllowance);
    }

    /** Parametry "ponad limit" przycinamy do 0..{@link #MAX_EXTRA}; brak parametru zostaje brakiem. */
    static Integer clampExtra(Integer extra) {
        if (extra == null) return null;
        return Math.max(0, Math.min(MAX_EXTRA, extra));
    }

    /** Dni od ostatniej powtórki; bez daty (pytanie bez historii) traktujemy jak bardzo dawno temu. */
    static double elapsedDays(LocalDateTime lastReviewAt, LocalDateTime now) {
        if (lastReviewAt == null) return Double.MAX_VALUE;
        return Math.max(0, Duration.between(lastReviewAt, now).toMillis() / 86_400_000.0);
    }
}
