package pl.gradusiuris.backend.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Włącza zadania cykliczne ({@code @Scheduled}), np. powrót pytań ze zmianą w prawie do kolejki. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
