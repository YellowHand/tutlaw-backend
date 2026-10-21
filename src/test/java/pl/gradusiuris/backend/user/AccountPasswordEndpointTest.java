package pl.gradusiuris.backend.user;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import pl.gradusiuris.backend.auth.AuthRateLimiter;
import pl.gradusiuris.backend.auth.AuthService;
import pl.gradusiuris.backend.auth.WrongPasswordException;
import pl.gradusiuris.backend.auth.dto.AuthResponse;
import pl.gradusiuris.backend.common.exception.TooManyRequestsException;
import pl.gradusiuris.backend.testsupport.FakeRepos;
import pl.gradusiuris.backend.testsupport.MutableClock;
import pl.gradusiuris.backend.user.dto.ChangePasswordRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** PUT /api/users/me/password: limit prób na zgadywanie obecnego hasła i zwrot świeżego tokenu. */
class AccountPasswordEndpointTest {

    private static final Authentication JAN = FakeRepos.authentication("jan@gmail.com");
    private static final HttpServletRequest HTTP = FakeRepos.proxy(HttpServletRequest.class, (method, args) -> {
        if (method.equals("getRemoteAddr")) {
            return "10.0.0.1";
        }
        throw new UnsupportedOperationException("HttpServletRequest." + method);
    });

    private final AuthService authService = mock(AuthService.class);
    private final AccountController controller = new AccountController(
            mock(AccountService.class), authService, new AuthRateLimiter(MutableClock.at("2026-10-13T08:00:00Z")));

    private static ChangePasswordRequest request() {
        return new ChangePasswordRequest("stare-haslo", "nowe-haslo-99");
    }

    @Test
    void returnsTheFreshTokenFromTheService() {
        when(authService.changePassword("jan@gmail.com", "stare-haslo", "nowe-haslo-99"))
                .thenReturn(new AuthResponse("nowy-token", "nowy-odswiezajacy"));

        AuthResponse response = controller.changePassword(request(), JAN, HTTP);

        assertThat(response.accessToken()).isEqualTo("nowy-token");
    }

    @Test
    void repeatedWrongCurrentPasswordsLockTheAttemptsForAWhile() {
        when(authService.changePassword("jan@gmail.com", "stare-haslo", "nowe-haslo-99"))
                .thenThrow(new WrongPasswordException("Obecne hasło jest nieprawidłowe"));

        for (int i = 0; i < 8; i++) {
            assertThatThrownBy(() -> controller.changePassword(request(), JAN, HTTP))
                    .isInstanceOf(WrongPasswordException.class);
        }
        assertThatThrownBy(() -> controller.changePassword(request(), JAN, HTTP))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void aWeakNewPasswordIsNotCountedAsAWrongGuess() {
        when(authService.changePassword("jan@gmail.com", "stare-haslo", "nowe-haslo-99"))
                .thenThrow(new IllegalArgumentException("Hasło musi mieć co najmniej 8 znaków"));

        for (int i = 0; i < 12; i++) {
            assertThatThrownBy(() -> controller.changePassword(request(), JAN, HTTP))
                    .isNotInstanceOf(WrongPasswordException.class)
                    .isNotInstanceOf(TooManyRequestsException.class);
        }
    }

    @Test
    void aSuccessfulChangeClearsEarlierFailures() {
        when(authService.changePassword("jan@gmail.com", "stare-haslo", "nowe-haslo-99"))
                .thenThrow(new WrongPasswordException("Obecne hasło jest nieprawidłowe"))
                .thenThrow(new WrongPasswordException("Obecne hasło jest nieprawidłowe"))
                .thenThrow(new WrongPasswordException("Obecne hasło jest nieprawidłowe"))
                .thenThrow(new WrongPasswordException("Obecne hasło jest nieprawidłowe"))
                .thenThrow(new WrongPasswordException("Obecne hasło jest nieprawidłowe"))
                .thenThrow(new WrongPasswordException("Obecne hasło jest nieprawidłowe"))
                .thenThrow(new WrongPasswordException("Obecne hasło jest nieprawidłowe"))
                .thenReturn(new AuthResponse("nowy-token", "nowy-odswiezajacy"));

        for (int i = 0; i < 7; i++) {
            assertThatThrownBy(() -> controller.changePassword(request(), JAN, HTTP))
                    .isInstanceOf(WrongPasswordException.class);
        }
        assertThat(controller.changePassword(request(), JAN, HTTP).accessToken()).isEqualTo("nowy-token");
    }
}
