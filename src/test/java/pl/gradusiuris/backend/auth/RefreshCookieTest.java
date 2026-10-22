package pl.gradusiuris.backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import pl.gradusiuris.backend.auth.dto.AccessTokenResponse;
import pl.gradusiuris.backend.auth.dto.AuthResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshCookieTest {

    @Test
    void theSessionCookieCannotBeReadByScriptsAndStaysOnTheSameSite() {
        String cookie = new RefreshCookie(true, 30).issue("abc");

        assertThat(cookie).startsWith("gradus_refresh=abc;")
                .contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/auth", "Max-Age=2592000");
        assertThat(cookie).doesNotContain("Domain=");
    }

    @Test
    void lifetimeFollowsTheConfiguredRefreshTokenLifetime() {
        assertThat(new RefreshCookie(true, 7).issue("abc")).contains("Max-Age=604800");
    }

    @Test
    void secureFlagIsOffOnlyWhenConfiguredForLocalDevelopment() {
        assertThat(new RefreshCookie(false, 30).issue("abc")).doesNotContain("Secure").contains("HttpOnly");
    }

    @Test
    void clearingExpiresTheCookieWithTheSameScope() {
        String cookie = new RefreshCookie(true, 30).clear();

        assertThat(cookie).startsWith("gradus_refresh=;").contains("Max-Age=0", "HttpOnly", "Secure", "Path=/api/auth");
    }

    @Test
    void sessionResponseCarriesOnlyTheAccessTokenInTheBody() {
        ResponseEntity<AccessTokenResponse> response = new RefreshCookie(true, 30).sessionResponse(new AuthResponse("jwt", "sekret"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(new AccessTokenResponse("jwt"));
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("gradus_refresh=sekret");
    }
}
