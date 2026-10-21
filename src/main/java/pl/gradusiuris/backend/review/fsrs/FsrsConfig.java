package pl.gradusiuris.backend.review.fsrs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FsrsConfig {

    @Bean
    public FsrsEngine fsrsEngine(@Value("${app.fsrs.request-retention}") double requestRetention) {
        return new FsrsEngine(FsrsWeights.defaults(), requestRetention);
    }
}