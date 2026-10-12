package pl.tutlaw.backend.subject.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SubjectRequest(@NotNull Long categoryId, @NotBlank String name, @NotBlank String slug) {
}