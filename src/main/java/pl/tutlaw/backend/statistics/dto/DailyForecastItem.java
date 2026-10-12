package pl.tutlaw.backend.statistics.dto;

import java.time.LocalDate;

public record DailyForecastItem(LocalDate date, long count) {
}