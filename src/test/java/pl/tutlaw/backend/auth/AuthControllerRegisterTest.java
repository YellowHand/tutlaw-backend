package pl.tutlaw.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import pl.tutlaw.backend.auth.dto.RegisterRequest;
import pl.tutlaw.backend.testsupport.FakeRepos;
import pl.tutlaw.backend.testsupport.MutableClock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

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
    private final AuthController controller =
            new AuthController(authService, new AuthRateLimiter(MutableClock.at("2026-10-13T08:00:00Z")));

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
    void otherProblemsAreStillReported() {
        doThrow(new IllegalArgumentException("Hasło musi mieć co najmniej 8 znaków")).when(authService).register(any());

        assertThatThrownBy(() -> controller.register(REQUEST, HTTP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("co najmniej 8");
    }
}
