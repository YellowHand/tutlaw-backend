package pl.gradusiuris.backend.admin.dto;

import jakarta.validation.constraints.NotNull;

/** Ukrycie ({@code true}) albo przywrócenie ({@code false}) aktu lub arkusza. */
public record HiddenRequest(@NotNull Boolean hidden) {
}
