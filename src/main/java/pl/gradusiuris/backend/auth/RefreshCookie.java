package pl.gradusiuris.backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import pl.gradusiuris.backend.auth.dto.AccessTokenResponse;
import pl.gradusiuris.backend.auth.dto.AuthResponse;

import java.time.Duration;

/**
 * Ciasteczko z tokenem odświeżającym. Token jest tylko tam, nigdy w treści odpowiedzi ani w pamięci przeglądarki
 * dostępnej dla skryptów:
 * <ul>
 *   <li>{@code HttpOnly}: skrypt na stronie (np. wstrzyknięty przez XSS) nie może go odczytać,</li>
 *   <li>{@code Secure}: wędruje tylko przez HTTPS (wyłączane wyłącznie lokalnie, profil dev),</li>
 *   <li>{@code SameSite=Strict}: przeglądarka wyśle je tylko ze strony z tej samej witryny (gradusiuris.pl i api.gradusiuris.pl
 *       to jedna witryna), więc obca strona nie może wymusić odświeżenia ani wylogowania,</li>
 *   <li>{@code Path=/api/auth}: dołączane wyłącznie do żądań logowania, odświeżania i wylogowania, a nie do każdego żądania.</li>
 * </ul>
 */
@Component
public class RefreshCookie {

    /** Nazwa ciasteczka. Stała, bo używa jej adnotacja @CookieValue. */
    public static final String NAME = "gradus_refresh";

    static final String PATH = "/api/auth";

    private final boolean secure;
    private final Duration maxAge;

    public RefreshCookie(
            @Value("${app.auth.refresh-cookie-secure:true}") boolean secure,
            @Value("${app.jwt.refresh-token-expiration-days:30}") long ttlDays
    ) {
        this.secure = secure;
        this.maxAge = Duration.ofDays(ttlDays);
    }

    /** Wartość nagłówka Set-Cookie, który zapisuje token w przeglądarce. */
    public String issue(String rawToken) {
        return base(rawToken).maxAge(maxAge).build().toString();
    }

    /** Wartość nagłówka Set-Cookie, który usuwa ciasteczko (wylogowanie, usunięcie konta). */
    public String clear() {
        return base("").maxAge(Duration.ZERO).build().toString();
    }

    /** 200 z tokenem dostępu w treści i tokenem odświeżającym w ciasteczku. */
    public ResponseEntity<AccessTokenResponse> sessionResponse(AuthResponse pair) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, issue(pair.refreshToken()))
                .body(new AccessTokenResponse(pair.accessToken()));
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH);
    }
}
