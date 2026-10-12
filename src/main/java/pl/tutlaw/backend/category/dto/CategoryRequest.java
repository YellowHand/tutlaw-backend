package pl.tutlaw.backend.category.dto;

import jakarta.validation.constraints.NotBlank;

public record CategoryRequest(@NotBlank String name, @NotBlank String slug) {
}