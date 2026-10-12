package pl.tutlaw.backend.category.dto;

import jakarta.validation.constraints.NotBlank;

/** itemsLabel i sortOrder są opcjonalne: bez nich kategoria dostaje „Przedmioty" i kolejność 100. */
public record CategoryRequest(@NotBlank String name, @NotBlank String slug, String itemsLabel, Integer sortOrder) {
}
