package pl.gradusiuris.backend.question.dto;

import jakarta.validation.constraints.NotNull;

/** Ukrycie (true) albo przywrócenie (false) pytania albo jego fiszki. */
public record HiddenRequest(@NotNull(message = "podaj, czy ukryć") Boolean hidden) {
}
