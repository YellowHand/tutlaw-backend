package pl.tutlaw.backend.mark.dto;

import jakarta.validation.constraints.NotNull;
import pl.tutlaw.backend.mark.MarkStatus;
import pl.tutlaw.backend.mark.QuestionMarkType;

import java.util.List;

/**
 * Hurtowa zmiana stanu oznaczeń: wszystkie oznaczenia w stanie {@code from} (tylko podanych rodzajów albo wszystkich,
 * gdy {@code marks} jest puste) dostają stan {@code to}. {@code to} to CONFIRMED albo REJECTED (do propozycji nie ma
 * powrotu), a {@code from} musi się różnić od {@code to}.
 */
public record BulkMarkRequest(
        @NotNull(message = "podaj stan, z którego zmieniasz") MarkStatus from,
        @NotNull(message = "podaj stan docelowy") MarkStatus to,
        List<QuestionMarkType> marks
) {
}
