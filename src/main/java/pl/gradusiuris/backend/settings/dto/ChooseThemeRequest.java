package pl.gradusiuris.backend.settings.dto;

import jakarta.validation.constraints.NotNull;
import pl.gradusiuris.backend.settings.Theme;

/** Wygląd aplikacji: ciemny ({@code DARK}) albo jasny ({@code LIGHT}). */
public record ChooseThemeRequest(@NotNull Theme theme) {
}
