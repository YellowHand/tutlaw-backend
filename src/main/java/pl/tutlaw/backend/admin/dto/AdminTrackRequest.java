package pl.tutlaw.backend.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dodanie albo zmiana aplikacji. Adres (slug) powstaje z nazwy przy dodaniu i się nie zmienia.
 *
 * @param sortOrder miejsce na liście (rosnąco); brak wartości przy dodaniu: na końcu, przy zmianie: bez zmiany
 */
public record AdminTrackRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 60) String shortName,
        @Min(0) @Max(100000) Integer sortOrder
) {
}
