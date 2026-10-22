package pl.gradusiuris.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import pl.gradusiuris.backend.auth.dto.AccessTokenResponse;
import pl.gradusiuris.backend.auth.dto.AuthResponse;
import pl.gradusiuris.backend.auth.dto.LoginRequest;
import pl.gradusiuris.backend.auth.dto.RegisterRequest;
import pl.gradusiuris.backend.common.exception.TooManyRequestsException;
import pl.gradusiuris.backend.common.exception.UnauthorizedException;
import pl.gradusiuris.backend.testsupport.FakeRepos;
import pl.gradusiuris.backend.testsupport.MutableClock;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** POST /api/auth/register zawsze odpowiada tak samo, także gdy równoległe żądanie wyprzedziło nas przy zakładaniu konta. */
class AuthControllerRegisterTest {

    private static final String FRONTEND = "https://gradusiuris.pl";

    private static HttpServletRequest http(String origin) {
        return FakeRepos.proxy(HttpServletRequest.class, (method, args) -> {
            if (method.equals("getRemoteAddr")) {
                return "10.0.0.1";
            }
            if (method.equals("getHeader") && HttpHeaders.ORIGIN.equals(args[0])) {
                return origin;
            }
            throw new UnsupportedOperationException("HttpServletRequest." + method);
        });
    }

    /** Żądanie z naszego frontendu (przeglądarka dołącza Origin). */
    private static final HttpServletRequest HTTP = http(FRONTEND);
    private static final RegisterRequest REQUEST = new RegisterRequest("jan@gmail.com", "jan@uni.pl", "haslo1234", true);

    private final AuthService authService = mock(AuthService.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final AuthController controller =
            new AuthController(authService, refreshTokenService, new AuthRateLimiter(MutableClock.at("2026-10-13T08:00:00Z")),
                    new RefreshCookie(true, 30), new TrustedOrigins(FRONTEND));

    @Test
    void successIsAPlain200() {
        ResponseEntity<Void> response = controller.register(REQUEST, HTTP);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void aRaceOnTheUniqueConstraintLooksLikeSuccessToo() {
        doThrow(new DataIntegrityViolationException("duplicate key")).when(authService).register(any());

        ResponseEntity<Void> response = controller.register(REQUEST, HTTP);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void loginPutsTheRefreshTokenInAnHttpOnlyCookieAndNotInTheBody() {
        when(authService.login(any())).thenReturn(new AuthResponse("jwt", "tajny-odswiezajacy"));

        ResponseEntity<AccessTokenResponse> response = controller.login(new LoginRequest("jan@gmail.com", "haslo1234"), HTTP);

        assertThat(response.getBody()).isEqualTo(new AccessTokenResponse("jwt"));
        assertThat(response.getBody().toString()).doesNotContain("tajny-odswiezajacy");
        String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(cookie).startsWith("gradus_refresh=tajny-odswiezajacy;")
                .contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/auth", "Max-Age=2592000");
    }

    @Test
    void refreshTakesTheTokenFromTheCookieAndAnswersWithANewCookie() {
        when(refreshTokenService.refresh("stary")).thenReturn(Optional.of(new AuthResponse("jwt", "nowy")));

        ResponseEntity<AccessTokenResponse> response = controller.refresh("stary", HTTP);

        assertThat(response.getBody().accessToken()).isEqualTo("jwt");
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).startsWith("gradus_refresh=nowy;").contains("HttpOnly");
    }

    @Test
    void refreshWithABadOrMissingCookieIs401() {
        when(refreshTokenService.refresh("zly")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.refresh("zly", HTTP))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Zaloguj się ponownie");
        assertThatThrownBy(() -> controller.refresh(null, HTTP)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void logoutRevokesTheSessionClearsTheCookieAndAlwaysAnswers204() {
        ResponseEntity<Void> response = controller.logout("cokolwiek", HTTP);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(refreshTokenService).revokeSession("cokolwiek");
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .startsWith("gradus_refresh=;").contains("Max-Age=0", "HttpOnly", "Path=/api/auth");
        assertThat(controller.logout(null, HTTP).getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void refreshAndLogoutFromAForeignSiteAreRefusedBeforeTouchingTheSession() {
        HttpServletRequest evil = http("https://evil.example");

        assertThatThrownBy(() -> controller.refresh("stary", evil))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(403));
        assertThatThrownBy(() -> controller.logout("stary", evil)).isInstanceOf(ResponseStatusException.class);
        verify(refreshTokenService, never()).refresh(any());
        verify(refreshTokenService, never()).revokeSession(any());
    }

    @Test
    void requestsWithoutAnOriginHeaderAreNotRefused() {
        when(refreshTokenService.refresh("stary")).thenReturn(Optional.of(new AuthResponse("jwt", "nowy")));

        assertThat(controller.refresh("stary", http(null)).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void refreshIsRateLimitedPerAddress() {
        when(refreshTokenService.refresh(any())).thenReturn(Optional.empty());
        for (int i = 0; i < AuthRateLimiter.REFRESH_PER_IP; i++) {
            assertThatThrownBy(() -> controller.refresh("x", HTTP))
                    .isInstanceOf(UnauthorizedException.class);
        }

        assertThatThrownBy(() -> controller.refresh("x", HTTP))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void otherProblemsAreStillReported() {
        doThrow(new IllegalArgumentException("Hasło musi mieć co najmniej 8 znaków")).when(authService).register(any());

        assertThatThrownBy(() -> controller.register(REQUEST, HTTP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("co najmniej 8");
    }
}
