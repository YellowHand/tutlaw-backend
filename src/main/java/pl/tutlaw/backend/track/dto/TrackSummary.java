package pl.tutlaw.backend.track.dto;

import pl.tutlaw.backend.track.Track;
import pl.tutlaw.backend.track.TrackKind;

/** Ścieżka nauki w skrócie: do list wyboru, etykiet i chipów. */
public record TrackSummary(Long id, String slug, String name, String shortName, TrackKind kind) {

    public static TrackSummary of(Track track) {
        return new TrackSummary(track.getId(), track.getSlug(), track.getName(), track.getShortName(), track.getKind());
    }
}
