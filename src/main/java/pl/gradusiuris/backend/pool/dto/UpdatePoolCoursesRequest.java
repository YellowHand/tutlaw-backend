package pl.gradusiuris.backend.pool.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Przedmioty, z których student chce się uczyć. Pusta lista (albo wszystkie przedmioty) zdejmuje wybór, czyli
 * w puli są wszystkie przedmioty, także dodane później przez administratora.
 */
public record UpdatePoolCoursesRequest(
        @NotNull(message = "lista przedmiotów jest wymagana")
        @Size(max = 200, message = "za dużo wybranych przedmiotów")
        List<Long> courseIds
) {
}
