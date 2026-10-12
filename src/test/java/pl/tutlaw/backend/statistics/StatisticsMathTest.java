package pl.tutlaw.backend.statistics;

import org.junit.jupiter.api.Test;
import pl.tutlaw.backend.statistics.dto.ActivityHeatmapItem;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StatisticsMathTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);

    @Test
    void percentageIsNullWhenThereIsNothingToCount() {
        assertThat(StatisticsMath.percentage(0, 0)).isNull();
    }

    @Test
    void percentageOfZeroCorrectOutOfSomeIsZeroNotNull() {
        assertThat(StatisticsMath.percentage(0, 5)).isEqualTo(0.0);
    }

    @Test
    void percentageComputesShare() {
        assertThat(StatisticsMath.percentage(3, 4)).isEqualTo(75.0);
        assertThat(StatisticsMath.percentage(1, 3)).isBetween(33.33, 33.34);
    }

    @Test
    void perDayReturnsOneItemPerDayOldestFirstEndingToday() {
        List<ActivityHeatmapItem> result = StatisticsMath.perDay(TODAY, 7, List.of());

        assertThat(result).hasSize(7);
        assertThat(result.get(0).date()).isEqualTo(TODAY.minusDays(6));
        assertThat(result.get(6).date()).isEqualTo(TODAY);
        assertThat(result.stream().mapToLong(ActivityHeatmapItem::reviewCount).sum()).isEqualTo(0L);
    }

    @Test
    void perDayCountsEventsOnTheirOwnDay() {
        List<LocalDateTime> times = Arrays.asList(
                TODAY.atTime(8, 0),
                TODAY.atTime(23, 59),
                TODAY.minusDays(1).atTime(12, 0),
                TODAY.minusDays(6).atStartOfDay());

        List<ActivityHeatmapItem> result = StatisticsMath.perDay(TODAY, 7, times);

        assertThat(result.get(6).reviewCount()).isEqualTo(2L);
        assertThat(result.get(5).reviewCount()).isEqualTo(1L);
        assertThat(result.get(0).reviewCount()).isEqualTo(1L);
        assertThat(result.get(3).reviewCount()).isEqualTo(0L);
    }

    @Test
    void perDayIgnoresEventsOutsideTheWindow() {
        List<ActivityHeatmapItem> result = StatisticsMath.perDay(
                TODAY, 3, List.of(TODAY.minusDays(10).atTime(10, 0)));

        assertThat(result.stream().mapToLong(ActivityHeatmapItem::reviewCount).sum()).isEqualTo(0L);
    }

    @Test
    void sumAddsAllValuesAndSkipsNulls() {
        assertThat(StatisticsMath.sum(Arrays.asList(5L, null, 7L))).isEqualTo(12L);
        assertThat(StatisticsMath.sum(List.<Long>of())).isEqualTo(0L);
    }
}
