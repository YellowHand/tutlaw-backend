package pl.gradusiuris.backend.common.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Bezpiecznik przy starcie: poza profilem {@code dev} aplikacja nie wstanie z ustawieniami, które wyglądają na lokalne
 * albo niebezpieczne (sekret z szablonu, krótki sekret, adres frontendu bez HTTPS). Chroni przed pomyłką typu „skopiowałem
 * lokalny plik .env na serwer”. W profilu {@code dev} nic nie sprawdza.
 */
@Component
public class ProductionSafetyCheck {

    /**
     * Fragmenty, po których poznajemy sekret z szablonów i dokumentacji (nie może trafić na produkcję).
     */
    private static final List<String> PLACEHOLDER_MARKERS = List.of("lokalny-dev", "zmien-", "changeme", "example", "wpisz");

    static final int MIN_SECRET_LENGTH = 32;

    private final Environment environment;
    private final String jwtSecret;
    private final String frontendUrl;
    private final String dbPassword;
    private final boolean refreshCookieSecure;

    public ProductionSafetyCheck(
            Environment environment,
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.frontend-url}") String frontendUrl,
            @Value("${spring.datasource.password:}") String dbPassword,
            @Value("${app.auth.refresh-cookie-secure:true}") boolean refreshCookieSecure
    ) {
        this.environment = environment;
        this.jwtSecret = jwtSecret;
        this.frontendUrl = frontendUrl;
        this.dbPassword = dbPassword;
        this.refreshCookieSecure = refreshCookieSecure;
    }

    @PostConstruct
    void check() {
        if (environment.acceptsProfiles(Profiles.of("dev"))) {
            return;
        }
        List<String> problems = problems(jwtSecret, frontendUrl, dbPassword, refreshCookieSecure);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Niebezpieczna konfiguracja poza profilem dev: " + String.join("; ", problems));
        }
    }

    /**
     * Lista problemów (pusta, gdy wszystko w porządku). Nie zawiera samych wartości, żeby sekret nie trafił do logu.
     */
    static List<String> problems(String jwtSecret, String frontendUrl, String dbPassword) {
        return problems(jwtSecret, frontendUrl, dbPassword, true);
    }

    static List<String> problems(String jwtSecret, String frontendUrl, String dbPassword, boolean refreshCookieSecure) {
        List<String> problems = new ArrayList<>();
        if (!refreshCookieSecure) {
            problems.add("REFRESH_COOKIE_SECURE=false: ciasteczko sesji bez flagi Secure wolno mieć tylko lokalnie (profil dev)");
        }
        if (jwtSecret == null || jwtSecret.length() < MIN_SECRET_LENGTH) {
            problems.add("APP_JWT_SECRET ma mniej niż " + MIN_SECRET_LENGTH + " znaków");
        } else if (looksLikePlaceholder(jwtSecret)) {
            problems.add("APP_JWT_SECRET wygląda na sekret z szablonu, wygeneruj własny losowy");
        }
        if (frontendUrl == null || !frontendUrl.startsWith("https://")) {
            problems.add("FRONTEND_URL musi zaczynać się od https:// (linki w mailach i CORS)");
        }
        if (dbPassword == null || dbPassword.isBlank()) {
            problems.add("hasło bazy (SPRING_DATASOURCE_PASSWORD) jest puste");
        } else if (looksLikePlaceholder(dbPassword)) {
            problems.add("hasło bazy wygląda na hasło z szablonu");
        }
        return problems;
    }

    private static boolean looksLikePlaceholder(String value) {
        String lower = value.toLowerCase();
        return PLACEHOLDER_MARKERS.stream().anyMatch(lower::contains)
                || lower.equals("gradusiuris_password");
    }
}
