package pl.tutlaw.backend.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Jeden zegar dla całej aplikacji. Strefę ustawia {@code BackendApplication.main}
 * (domyślnie Europe/Warsaw), więc "dzisiaj" jest takie samo na każdym serwerze.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
