package pl.tutlaw.backend.statistics.dto;

import java.time.LocalDate;

public record ActivityHeatmapItem(LocalDate date, long reviewCount) {
}