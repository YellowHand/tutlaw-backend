package pl.tutlaw.backend.tag.dto;

import jakarta.validation.constraints.NotBlank;

public record TagRequest(@NotBlank String name) {}
