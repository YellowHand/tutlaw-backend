package pl.gradusiuris.backend.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pl.gradusiuris.backend.legal.LegalActKind;

/**
 * Dodanie albo zmiana aktu prawnego. Adres (slug) powstaje z nazwy przy dodaniu i się nie zmienia.
 *
 * @param shortName skrót na kafelkach (np. „KC"); pusty lub brak: akt nie ma skrótu
 * @param kind      ACT (kodeks, ustawa) albo TOPIC (zagadnienie, np. prawoznawstwo); brak: ACT przy dodaniu, bez zmiany przy edycji
 * @param sortOrder miejsce na liście w dziedzinie; brak: na końcu przy dodaniu, bez zmiany przy edycji
 */
public record AdminActRequest(
        @NotNull Long fieldId,
        @NotBlank @Size(max = 300) String name,
        @Size(max = 50) String shortName,
        LegalActKind kind,
        @Min(0) @Max(100000) Integer sortOrder
) {
}
