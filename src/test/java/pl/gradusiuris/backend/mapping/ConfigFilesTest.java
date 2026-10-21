package pl.gradusiuris.backend.mapping;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Testy statyczne plików konfiguracji. Plik bazowy ({@code application.yml}) obowiązuje na produkcji, więc ma być „ostry”:
 * sekrety i adresy bez wartości domyślnych (brak zmiennej = aplikacja nie startuje), bez Swaggera i {@code show-sql}.
 * Wygody dla programisty żyją w {@code application-dev.yml}, który włącza się jawnie ({@code SPRING_PROFILES_ACTIVE=dev}).
 */
class ConfigFilesTest {

    private static final Path RESOURCES = Path.of("src", "main", "resources");

    /** Zmienne, które nigdy nie mogą mieć wartości domyślnej w pliku bazowym. */
    private static final List<String> MUST_BE_PROVIDED = List.of(
            "SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD",
            "APP_JWT_SECRET", "FRONTEND_URL", "MAIL_HOST", "MAIL_FROM");

    private static String read(String name) throws IOException {
        return Files.readString(RESOURCES.resolve(name), StandardCharsets.UTF_8);
    }

    @Test
    void baseConfigHasNoDefaultsForSecretsAndAddresses() throws IOException {
        assumeTrue(Files.isRegularFile(RESOURCES.resolve("application.yml")), "Test uruchomiony poza katalogiem modułu");
        String base = read("application.yml");
        List<String> problems = new ArrayList<>();
        for (String variable : MUST_BE_PROVIDED) {
            Matcher withDefault = Pattern.compile("\\$\\{" + variable + ":").matcher(base);
            if (withDefault.find()) {
                problems.add(variable + " ma wartość domyślną w application.yml");
            }
            if (!base.contains("${" + variable + "}")) {
                problems.add(variable + " nie jest wymagane w application.yml");
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    void baseConfigIsProductionSafe() throws IOException {
        assumeTrue(Files.isRegularFile(RESOURCES.resolve("application.yml")), "Test uruchomiony poza katalogiem modułu");
        String base = read("application.yml");
        assertThat(base).doesNotContain("localhost").doesNotContain("lokalny-dev").doesNotContain("show-sql: true");
        assertThat(base).contains("show-sql: false");
        assertThat(base).contains("show-details: never");
        assertThat(base).contains("clean-disabled: true");
        // Stan zdrowia nie może zależeć od serwera poczty (healthcheck Dockera i monitoring pytają o niego co kilkanaście sekund).
        assertThat(base).containsPattern("mail:\\s*\\n\\s*enabled: false");
        assertThat(base).containsPattern("api-docs:\\s*\\n\\s*enabled: false");
        assertThat(base).containsPattern("swagger-ui:\\s*\\n\\s*enabled: false");
        assertThat(base).containsPattern("include: health\\s*(\\n|$)");
    }

    @Test
    void devProfileExistsAndOnlyLoosensLocalThings() throws IOException {
        assumeTrue(Files.isRegularFile(RESOURCES.resolve("application.yml")), "Test uruchomiony poza katalogiem modułu");
        assertThat(Files.isRegularFile(RESOURCES.resolve("application-dev.yml"))).isTrue();
        String dev = read("application-dev.yml");
        assertThat(dev).contains("${MAIL_HOST:localhost}").contains("${MAIL_PORT:1025}");     // Mailpit
        assertThat(dev).contains("http://localhost:5173");
        assertThat(dev).containsPattern("swagger-ui:\\s*\\n\\s*enabled: true");
    }

    @Test
    void noProductionProfileFileCarriesSecrets() throws IOException {
        assumeTrue(Files.isRegularFile(RESOURCES.resolve("application.yml")), "Test uruchomiony poza katalogiem modułu");
        // Produkcja = plik bazowy + zmienne środowiskowe. Osobny plik prod nie jest potrzebny, a gdyby powstał, nie może mieć sekretów.
        Path prod = RESOURCES.resolve("application-prod.yml");
        if (Files.exists(prod)) {
            assertThat(Files.readString(prod, StandardCharsets.UTF_8)).doesNotContainPattern("(?i)(password|secret):\\s*[^$\\s]");
        }
    }

    @Test
    void securityConfigEnablesCorsFilter() throws IOException {
        Path security = Path.of("src", "main", "java", "pl", "gradusiuris", "backend", "common", "config", "SecurityConfig.java");
        assumeTrue(Files.isRegularFile(security), "Test uruchomiony poza katalogiem modułu");
        // Sama obecność beana CorsConfigurationSource nie wystarcza: Spring Security dokłada filtr CORS dopiero po .cors(...).
        assertThat(Files.readString(security, StandardCharsets.UTF_8)).contains(".cors(");
    }
}
