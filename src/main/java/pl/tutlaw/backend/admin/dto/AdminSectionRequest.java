package pl.tutlaw.backend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Dodanie albo zmiana nazwy rozdziału. Nazwa zawiera numer rozdziału, tak jak ma się pokazać użytkownikowi. */
public record AdminSectionRequest(@NotBlank @Size(max = 300) String name) {
}
