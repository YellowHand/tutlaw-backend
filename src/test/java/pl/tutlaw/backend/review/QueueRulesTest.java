package pl.tutlaw.backend.review;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QueueRulesTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 10, 0);

    @Test
    void lowestRetrievabilityComesFirstThenOlderDueDateThenSmallerId() {
        List<QueueRules.DueItem> ordered = QueueRules.prioritize(List.of(
                new QueueRules.DueItem(5L, NOW.minusDays(1), 0.80),
                new QueueRules.DueItem(4L, NOW.minusDays(9), 0.85),
                new QueueRules.DueItem(3L, NOW.minusDays(2), 0.50),
                new QueueRules.DueItem(2L, NOW.minusDays(2), 0.80),
                new QueueRules.DueItem(1L, NOW.minusDays(2), 0.80)));

        // 3 ma najmniejszą szansę (0,50). Potem trzy karty po 0,80: najpierw starszy termin (1 i 2, remis rozstrzyga id), potem 5.
        // Karta 4 ma najwyższą szansę (0,85), więc mimo najstarszego terminu jest ostatnia.
        assertThat(ordered.stream().map(QueueRules.DueItem::cardId).toList()).containsExactly(3L, 1L, 2L, 5L, 4L);
    }

    @Test
    void remainingAllowanceIsTheDailyLimitMinusWhatIsAlreadyDone() {
        assertThat(QueueRules.remainingAllowance(100, 0, null)).isEqualTo(100);
        assertThat(QueueRules.remainingAllowance(100, 37, null)).isEqualTo(63);
        assertThat(QueueRules.remainingAllowance(100, 100, null)).isEqualTo(0);
        // Po przekroczeniu limitu (np. po "Powtórz jeszcze") nie schodzimy poniżej zera.
        assertThat(QueueRules.remainingAllowance(100, 140, null)).isEqualTo(0);
    }

    @Test
    void extraReviewsIgnoreTheDailyLimit() {
        assertThat(QueueRules.remainingAllowance(100, 100, 20)).isEqualTo(20);
        assertThat(QueueRules.remainingAllowance(100, 500, 20)).isEqualTo(20);
    }

    @Test
    void extraValuesAreClampedToAReasonableRange() {
        assertThat(QueueRules.clampExtra(null)).isNull();
        assertThat(QueueRules.clampExtra(-5)).isEqualTo(0);
        assertThat(QueueRules.clampExtra(50)).isEqualTo(50);
        assertThat(QueueRules.clampExtra(1_000_000)).isEqualTo(QueueRules.MAX_EXTRA);
    }

    @Test
    void deferredIsWhatDoesNotFitInTheAllowance() {
        assertThat(QueueRules.deferred(340, 100)).isEqualTo(240L);
        assertThat(QueueRules.deferred(50, 100)).isEqualTo(0L);
        assertThat(QueueRules.deferred(240, 0)).isEqualTo(240L);
    }

    @Test
    void elapsedDaysIsFractionalAndNeverNegative() {
        assertThat(QueueRules.elapsedDays(NOW.minusHours(12), NOW)).isEqualTo(0.5);
        assertThat(QueueRules.elapsedDays(NOW.plusHours(3), NOW)).isEqualTo(0.0);
        assertThat(QueueRules.elapsedDays(null, NOW)).isGreaterThan(1_000_000.0);
    }
}
