package pl.gradusiuris.backend.common.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    @Test
    void parsesOneAddress() {
        assertThat(CorsConfig.parseOrigins("https://gradusiuris.pl")).containsExactly("https://gradusiuris.pl");
    }

    @Test
    void parsesSeveralAddresses_trimmingSpacesAndTrailingSlashes() {
        assertThat(CorsConfig.parseOrigins(" https://gradusiuris.pl/ , https://www.gradusiuris.pl ,, "))
                .containsExactly("https://gradusiuris.pl", "https://www.gradusiuris.pl");
    }

    @Test
    void emptyOrMissingValueAllowsNoOrigin_insteadOfEverything() {
        assertThat(CorsConfig.parseOrigins("")).isEmpty();
        assertThat(CorsConfig.parseOrigins(null)).isEqualTo(List.of());
        assertThat(CorsConfig.parseOrigins("*")).containsExactly("*");
    }
}
