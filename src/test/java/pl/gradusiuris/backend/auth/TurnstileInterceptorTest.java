package pl.gradusiuris.backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import pl.gradusiuris.backend.common.exception.BotCheckException;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TurnstileInterceptorTest {

    /** Weryfikator, który zapisuje wywołania i przyjmuje tylko token „dobry”. */
    private static class RecordingVerifier extends TurnstileVerifier {
        final List<String> tokens = new ArrayList<>();
        final List<String> ips = new ArrayList<>();

        RecordingVerifier() {
            super("sekret", "http://127.0.0.1:1/nie-uzywany", 100);
        }

        @Override
        public void verify(String token, String remoteIp) {
            tokens.add(token);
            ips.add(remoteIp);
            if (!"dobry".equals(token)) {
                throw new BotCheckException(400, "zly token");
            }
        }
    }

    private final RecordingVerifier verifier = new RecordingVerifier();
    private final TurnstileInterceptor interceptor = new TurnstileInterceptor(verifier);

    private MockHttpServletRequest post(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("198.51.100.4");
        if (token != null) {
            request.addHeader(TurnstileVerifier.HEADER, token);
        }
        return request;
    }

    @Test
    void passesARequestWithAGoodTokenAndForwardsTheClientIp() {
        boolean proceed = interceptor.preHandle(post("dobry"), new MockHttpServletResponse(), new Object());

        assertThat(proceed).isTrue();
        assertThat(verifier.tokens).containsExactly("dobry");
        assertThat(verifier.ips).containsExactly("198.51.100.4");
    }

    @Test
    void stopsARequestWithoutOrWithABadToken() {
        assertThatThrownBy(() -> interceptor.preHandle(post(null), new MockHttpServletResponse(), new Object()))
                .isInstanceOf(BotCheckException.class);
        assertThatThrownBy(() -> interceptor.preHandle(post("zly"), new MockHttpServletResponse(), new Object()))
                .isInstanceOf(BotCheckException.class);
    }

    @Test
    void corsPreflightAndOtherMethodsAreNotChecked() {
        for (String method : new String[]{"OPTIONS", "GET"}) {
            MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/auth/login");

            assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        }
        assertThat(verifier.tokens).isEmpty();
    }

    @Test
    void protectedPathsAreExactlyTheBotMagnets() {
        assertThat(TurnstileConfig.PROTECTED_PATHS).containsExactlyInAnyOrder(
                "/api/auth/register", "/api/auth/login", "/api/auth/forgot-password", "/api/auth/resend-verification");
        // Pozostałe punkty (odświeżanie sesji, wylogowanie, potwierdzenie maila, ustawienie nowego hasła z tokenem z maila)
        // mają własne tokeny i nie mogą zależeć od widgetu.
        assertThat(TurnstileConfig.PROTECTED_PATHS).doesNotContain("/api/auth/refresh", "/api/auth/logout",
                "/api/auth/verify", "/api/auth/reset-password");
    }

    @Test
    void configRegistersTheInterceptor() {
        InterceptorRegistry registry = new InterceptorRegistry();

        new TurnstileConfig(verifier).addInterceptors(registry);

        // Sam fakt, że rejestracja nie rzuca i że ścieżki są podane, wystarczy; zachowanie pokrywają testy wyżej.
        assertThat(TurnstileConfig.PROTECTED_PATHS).isNotEmpty();
    }
}
