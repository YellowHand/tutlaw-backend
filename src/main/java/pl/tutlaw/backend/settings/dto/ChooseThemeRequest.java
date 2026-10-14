package pl.tutlaw.backend.settings.dto;

import jakarta.validation.constraints.NotNull;
import pl.tutlaw.backend.settings.Theme;

/** Wygląd aplikacji: ciemny ({@code DARK}) albo jasny ({@code LIGHT}). */
public record ChooseThemeRequest(@NotNull Theme theme) {
}
