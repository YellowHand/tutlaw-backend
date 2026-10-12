package pl.tutlaw.backend.testsupport;

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
