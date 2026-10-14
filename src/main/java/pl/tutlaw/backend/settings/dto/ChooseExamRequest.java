package pl.tutlaw.backend.settings.dto;

import jakarta.validation.constraints.NotNull;
import pl.tutlaw.backend.card.QuestionOrigin;

/** Aplikacja, do której użytkownik się przygotowuje (jedna z pięciu, bez pytań autorskich). */
public record ChooseExamRequest(@NotNull QuestionOrigin origin) {
}
