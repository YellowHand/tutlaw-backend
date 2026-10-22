package pl.gradusiuris.backend.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS: które strony mogą wołać to API z przeglądarki. Dozwolony adres frontendu pochodzi z konfiguracji
 * ({@code app.cors.allowed-origin}, zmienna {@code FRONTEND_URL}), więc lokalnie to {@code http://localhost:5173}, a na produkcji
 * adres strony (np. {@code https://gradusiuris.pl}). Można podać kilka adresów po przecinku (np. z {@code www}).
 */
@Configuration
public class CorsConfig {

    /** Ile sekund przeglądarka może pamiętać odpowiedź na zapytanie wstępne (OPTIONS), żeby nie powtarzać go przy każdym żądaniu. */
    private static final long PREFLIGHT_MAX_AGE_SECONDS = 3600;

    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origin}") String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(parseOrigins(allowedOrigins));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(PREFLIGHT_MAX_AGE_SECONDS);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /** Adresy po przecinku, bez spacji i bez ukośnika na końcu (przeglądarka wysyła Origin bez niego). Puste wpisy znikają. */
    public static List<String> parseOrigins(String value) {
        if (value == null) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .map(origin -> origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }
}
