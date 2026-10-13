package pl.tutlaw.backend.settings.dto;

import pl.tutlaw.backend.card.QuestionOrigin;

import java.util.List;

/**
 * Filtr pochodzenia pytań w Testach.
 *
 * @param selected wybrane pochodzenia (zawsze co najmniej jedno), w kolejności enumu
 * @param all      czy wybrane są wszystkie (stan domyślny)
 * @param options  wszystkie pochodzenia z liczbą pytań testowych (bez ukrytych), do okna wyboru
 */
public record OriginFilterResponse(List<QuestionOrigin> selected, boolean all, List<Option> options) {

    public record Option(QuestionOrigin origin, long questionCount) {
    }
}
