package pl.gradusiuris.backend.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Nowy arkusz: aplikacja i rok. Nazwa i adres powstają z nich (np. „Notarialna 2024", {@code notarialna-2024}). */
public record AdminSheetCreateRequest(
        @NotNull Long trackId,
        @NotNull @Min(1990) @Max(2100) Integer examYear
) {
}
