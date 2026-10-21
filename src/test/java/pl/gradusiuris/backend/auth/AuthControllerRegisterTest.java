package pl.gradusiuris.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import pl.gradusiuris.backend.auth.dto.AuthResponse;
import pl.gradusiuris.backend.auth.dto.RefreshRequest;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** POST /api/auth/register zawsze odpowiada tak samo, także gdy równoległe żądanie wyprzedziło nas przy zakładaniu konta. */
class AuthControllerRegisterTest {

    private static final HttpServletRequest HTTP = FakeRepos.proxy(HttpServletRequest.class, (method, args) -> {
        if (method.equals("getRemoteAddr")) {
            return "10.0.0.1";
        }
        throw new UnsupportedOperationException("HttpServletRequest." + method);
    });
    private static final RegisterRequest REQUEST = new RegisterRequest("jan@gmail.com", "jan@uni.pl", "haslo1234", true);

    private final AuthService authService = mock(AuthService.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final AuthController controller =
            new AuthController(authService, refreshTokenService, new AuthRateLimiter(MutableClock.at("2026-10-13T08:00:00Z")));

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
    void refreshReturnsTheNewPair() {
        when(refreshTokenService.refresh("stary")).thenReturn(Optional.of(new AuthResponse("jwt", "nowy")));

        AuthResponse response = controller.refresh(new RefreshRequest("stary"), HTTP);

        assertThat(response.accessToken()).isEqualTo("jwt");
        assertThat(response.refreshToken()).isEqualTo("nowy");
    }

    @Test
    void refreshWithABadTokenIs401() {
        when(refreshTokenService.refresh("zly")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.refresh(new RefreshRequest("zly"), HTTP))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Zaloguj się ponownie");
    }

    @Test
    void logoutRevokesTheSessionAndAlwaysAnswers204() {
        ResponseEntity<Void> response = controller.logout(new RefreshRequest("cokolwiek"), HTTP);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(refreshTokenService).revokeSession("cokolwiek");
    }

    @Test
    void refreshIsRateLimitedPerAddress() {
        when(refreshTokenService.refresh(any())).thenReturn(Optional.empty());
        for (int i = 0; i < AuthRateLimiter.REFRESH_PER_IP; i++) {
            assertThatThrownBy(() -> controller.refresh(new RefreshRequest("x"), HTTP))
                    .isInstanceOf(UnauthorizedException.class);
        }

        assertThatThrownBy(() -> controller.refresh(new RefreshRequest("x"), HTTP))
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
