package pl.gradusiuris.backend.track.dto;

import pl.gradusiuris.backend.track.Track;
import pl.gradusiuris.backend.track.TrackKind;

/** Ścieżka nauki w skrócie: do list wyboru, etykiet i chipów. */
public record TrackSummary(Long id, String slug, String name, String shortName, TrackKind kind) {

    public static TrackSummary of(Track track) {
        return new TrackSummary(track.getId(), track.getSlug(), track.getName(), track.getShortName(), track.getKind());
    }
}
