package pl.gradusiuris.backend.pool.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Akty prawne, do których użytkownik zawęża pulę. Pusta lista (albo wszystkie akty ścieżki) zdejmuje zawężenie.
 */
public record UpdatePoolActsRequest(
        @NotNull(message = "lista aktów jest wymagana")
        @Size(max = 1000, message = "za dużo wybranych aktów prawnych")
        List<Long> actIds
) {
}
