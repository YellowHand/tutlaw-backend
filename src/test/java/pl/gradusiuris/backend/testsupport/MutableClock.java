package pl.gradusiuris.backend.testsupport;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** Zegar do testów, który można przesuwać w czasie. */
public final class MutableClock extends Clock {

    private final ZoneId zone;
    private Instant now;

    public MutableClock(Instant start, ZoneId zone) {
        this.now = start;
        this.zone = zone;
    }

    /** Zegar ustawiony na podaną chwilę (ISO, np. {@code 2026-10-12T10:00:00Z}), w strefie Europe/Warsaw. */
    public static MutableClock at(String isoInstant) {
        return new MutableClock(Instant.parse(isoInstant), ZoneId.of("Europe/Warsaw"));
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        MutableClock copy = new MutableClock(now, newZone);
        return copy;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
