package pl.gradusiuris.backend.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.gradusiuris.backend.common.exception.BotCheckException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Sprawdza w Cloudflare Turnstile, czy żądanie pochodzi od człowieka (token z widgetu „checkbox” na stronie).
 * Token jest jednorazowy i ważny kilka minut; Cloudflare potwierdza go po stronie serwera (siteverify).
 *
 * Bez sekretu (TURNSTILE_SECRET puste, np. lokalnie) sprawdzenie jest wyłączone i niczego nie blokuje. Gdy sekret jest ustawiony,
 * brak tokenu albo jego odrzucenie to 400, a niedostępność Cloudflare to 503 (zamykamy, nie otwieramy: awaria nie ma
 * zamieniać się w otwartą furtkę dla botów).
 */
@Component
@Slf4j
public class TurnstileVerifier {

    /** Nagłówek, w którym frontend przysyła token z widgetu. */
    public static final String HEADER = "X-Turnstile-Token";

    /** Cloudflare dopuszcza tokeny do 2048 znaków; dłuższe odrzucamy od razu, bez pytania Cloudflare. */
    static final int MAX_TOKEN_LENGTH = 2048;

    private static final Pattern SUCCESS = Pattern.compile("\"success\"\\s*:\\s*true");

    private final String secret;
    private final URI verifyUrl;
    private final Duration timeout;
    private final HttpClient client;

    public TurnstileVerifier(
            @Value("${app.turnstile.secret:}") String secret,
            @Value("${app.turnstile.verify-url:https://challenges.cloudflare.com/turnstile/v0/siteverify}") String verifyUrl,
            @Value("${app.turnstile.timeout-ms:5000}") long timeoutMs
    ) {
        this.secret = secret == null ? "" : secret.trim();
        this.verifyUrl = URI.create(verifyUrl);
        this.timeout = Duration.ofMillis(timeoutMs);
        this.client = HttpClient.newBuilder().connectTimeout(this.timeout).build();
        if (this.secret.isEmpty()) {
            log.warn("Turnstile jest wyłączony (brak TURNSTILE_SECRET): rejestracja i logowanie nie sprawdzają, czy to człowiek");
        }
    }

    public boolean isEnabled() {
        return !secret.isEmpty();
    }

    /** Rzuca {@link BotCheckException}, gdy sprawdzenie jest włączone i się nie powiodło. */
    public void verify(String token, String remoteIp) {
        if (!isEnabled()) {
            return;
        }
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            throw new BotCheckException(400, "Potwierdź, że nie jesteś robotem.");
        }

        StringBuilder form = new StringBuilder()
                .append("secret=").append(encode(secret))
                .append("&response=").append(encode(token));
        if (remoteIp != null && !remoteIp.isBlank()) {
            form.append("&remoteip=").append(encode(remoteIp));
        }
        HttpRequest request = HttpRequest.newBuilder(verifyUrl)
                .timeout(timeout)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
                .build();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw unavailable("błąd połączenia: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable("przerwano oczekiwanie");
        }
        if (response.statusCode() != 200) {
            throw unavailable("odpowiedź HTTP " + response.statusCode());
        }
        if (!SUCCESS.matcher(response.body()).find()) {
            throw new BotCheckException(400, "Weryfikacja nie powiodła się. Odśwież stronę i spróbuj jeszcze raz.");
        }
    }

    private static BotCheckException unavailable(String reason) {
        // Bez sekretu i tokenu w logu. ERROR trafia do Sentry: wiemy, że Cloudflare nie odpowiada, a użytkownicy nie mogą się zalogować.
        log.error("Nie udało się sprawdzić Turnstile ({})", reason);
        return new BotCheckException(503, "Nie udało się teraz sprawdzić zabezpieczenia. Spróbuj za chwilę.");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
