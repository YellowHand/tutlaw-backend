package pl.tutlaw.backend.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Dodanie albo zmiana przedmiotu Studenta. Adres (slug) powstaje z nazwy przy dodaniu i się nie zmienia. */
public record AdminCourseRequest(
        @NotBlank @Size(max = 255) String name,
        @Min(0) @Max(100000) Integer sortOrder
) {
}
