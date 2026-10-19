package pl.tutlaw.backend.pool.dto;

import jakarta.validation.constraints.NotNull;

/** Filtr źródeł w Testach: które z trzech grup mają dawać pytania. Co najmniej jedna musi być włączona. */
public record UpdateSourceFilterRequest(
        @NotNull(message = "wybór własnych arkuszy jest wymagany") Boolean ownSheets,
        @NotNull(message = "wybór arkuszy innych ścieżek jest wymagany") Boolean otherSheets,
        @NotNull(message = "wybór pytań autorskich jest wymagany") Boolean authored
) {
}
