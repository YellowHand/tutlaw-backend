package pl.gradusiuris.backend.statistics;

import pl.gradusiuris.backend.statistics.dto.ActivityHeatmapItem;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Czysta logika statystyk (bez Springa i bazy), dzięki czemu da się ją przetestować samym JUnit-em.
 */
final class StatisticsMath {

    private StatisticsMath() {
    }

    /**
     * Procent {@code part} z {@code whole}. Gdy nie ma z czego liczyć ({@code whole == 0}), zwraca {@code null},
     * czyli „brak danych". Wcześniej w takiej sytuacji pokazywało się 0%, co wyglądało jak same błędy.
     */
    static Double percentage(long part, long whole) {
        if (whole <= 0) return null;
        return part * 100.0 / whole;
    }

    /**
     * Liczba zdarzeń na każdy z ostatnich {@code days} dni (łącznie z dzisiejszym), od najstarszego do dzisiaj.
     * Dni bez zdarzeń mają 0, więc wykres nie ma dziur.
     */
    static List<ActivityHeatmapItem> perDay(LocalDate today, int days, List<LocalDateTime> times) {
        Map<LocalDate, Long> countsByDate = times.stream()
                .collect(Collectors.groupingBy(LocalDateTime::toLocalDate, Collectors.counting()));

        List<ActivityHeatmapItem> result = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            result.add(new ActivityHeatmapItem(date, countsByDate.getOrDefault(date, 0L)));
        }
        return result;
    }

    static long sum(Iterable<Long> values) {
        long total = 0;
        for (Long value : values) {
            if (value != null) total += value;
        }
        return total;
    }
}
