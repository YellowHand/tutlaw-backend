package pl.tutlaw.backend.review;

import org.springframework.stereotype.Component;

import java.util.Random;

/** Losowy rozrzut terminów powtórek ({@link SchedulingRules#fuzz}). Osobna klasa, żeby w testach podstawić stały generator. */
@Component
public class IntervalFuzzer {

    private final Random random;

    public IntervalFuzzer() {
        this(new Random());
    }

    IntervalFuzzer(Random random) {
        this.random = random;
    }

    public double apply(double intervalDays) {
        return SchedulingRules.fuzz(intervalDays, random.nextDouble());
    }
}
