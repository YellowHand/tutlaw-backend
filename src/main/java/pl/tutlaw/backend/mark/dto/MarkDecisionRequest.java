package pl.tutlaw.backend.mark.dto;

import jakarta.validation.constraints.NotNull;
import pl.tutlaw.backend.mark.MarkStatus;

/** Decyzja administratora o jednym oznaczeniu: CONFIRMED (pytanie ma je mieć) albo REJECTED (nie ma go mieć). */
public record MarkDecisionRequest(@NotNull(message = "podaj decyzję") MarkStatus status) {
}
