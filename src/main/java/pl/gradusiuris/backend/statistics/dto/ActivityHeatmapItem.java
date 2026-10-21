package pl.gradusiuris.backend.statistics.dto;

import java.time.LocalDate;

public record ActivityHeatmapItem(LocalDate date, long reviewCount) {
}