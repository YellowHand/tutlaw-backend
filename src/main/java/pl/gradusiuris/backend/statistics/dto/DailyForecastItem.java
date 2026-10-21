package pl.gradusiuris.backend.statistics.dto;

import java.time.LocalDate;

public record DailyForecastItem(LocalDate date, long count) {
}