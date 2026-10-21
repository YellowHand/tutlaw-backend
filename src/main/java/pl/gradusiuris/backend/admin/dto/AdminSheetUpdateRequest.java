package pl.gradusiuris.backend.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Zmiana arkusza.
 *
 * @param examYear rok egzaminu
 * @param name     nazwa widoczna dla użytkownika; pusta albo brak: nazwa powstaje z aplikacji i roku
 */
public record AdminSheetUpdateRequest(
        @NotNull @Min(1990) @Max(2100) Integer examYear,
        @Size(max = 255) String name
) {
}
