package pl.gradusiuris.backend.settings.dto;

import jakarta.validation.constraints.NotNull;

/** Wybór w oknie startowym i w Koncie: ścieżka (jedna z pięciu aplikacji albo Student). */
public record ChooseTrackRequest(@NotNull(message = "wybierz ścieżkę") Long trackId) {
}
