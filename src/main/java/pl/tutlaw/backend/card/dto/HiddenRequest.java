package pl.tutlaw.backend.card.dto;

import jakarta.validation.constraints.NotNull;

/** Ukrycie (true) albo przywrócenie (false) karty. */
public record HiddenRequest(@NotNull(message = "podaj, czy ukryć kartę") Boolean hidden) {
}
