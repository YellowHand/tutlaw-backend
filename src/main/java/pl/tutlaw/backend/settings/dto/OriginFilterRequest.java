package pl.tutlaw.backend.settings.dto;

import jakarta.validation.constraints.NotNull;
import pl.tutlaw.backend.card.QuestionOrigin;

import java.util.List;

/** Wybrane pochodzenia pytań w Testach. Co najmniej jedno; powtórzenia są ignorowane. */
public record OriginFilterRequest(@NotNull List<QuestionOrigin> origins) {
}
