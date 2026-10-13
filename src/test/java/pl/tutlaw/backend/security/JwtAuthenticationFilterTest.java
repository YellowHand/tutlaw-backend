package pl.tutlaw.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import pl.tutlaw.backend.auth.JwtService;
import pl.tutlaw.backend.testsupport.FakeRepos;
import pl.tutlaw.backend.user.UserRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Filtr JWT: poprawny token to logowanie, chyba że konto zniknęło albo token jest starszy niż ostatnia zmiana hasła. */
class JwtAuthenticationFilterTest {

    @AfterEach
    void clean() {
        SecurityContextHolder.clearContext();
    }

    /** Token "ważny podpisem" dla jan@gmail.com, wystawiony w podanej sekundzie. */
    private static JwtService jwt(boolean signatureOk, Long issuedAt) {
        return new JwtService("0123456789abcdef0123456789abcdef-test-secret", 60_000) {
            @Override
            public boolean isTokenValid(String token) {
                return signatureOk;
            }

            @Override
            public String extractEmail(String token) {
                return "jan@gmail.com";
            }

            @Override
            public String extractRole(String token) {
                return "STUDENT";
            }

            @Override
            public Long extractIssuedAtEpochSeconds(String token) {
                return issuedAt;
            }
        };
    }

    private static final class Run {
        final List<String> lookups = new ArrayList<>();
        int chainCalls = 0;
        Authentication authentication;
    }

    /** accountExists/validFrom opisują wiersz w bazie; header = nagłówek Authorization (null = brak). */
    private static Run run(JwtService jwt, boolean accountExists, Long validFrom, String header) throws Exception {
        Run result = new Run();
        UserRepository users = FakeRepos.proxy(UserRepository.class, (method, args) -> {
            if (!method.equals("findSessionInfoByPrivateEmail")) {
                throw new UnsupportedOperationException("UserRepository." + method);
            }
            result.lookups.add((String) args[0]);
            if (!accountExists) {
                return Optional.empty();
            }
            UserRepository.SessionInfo info = new UserRepository.SessionInfo() {
                @Override
                public Long getId() {
                    return 1L;
                }

                @Override
                public Long getTokensValidFrom() {
                    return validFrom;
                }
            };
            return Optional.of(info);
        });
        HttpServletRequest request = FakeRepos.proxy(HttpServletRequest.class, (method, args) -> {
            if (method.equals("getHeader")) {
                return header;
            }
            throw new UnsupportedOperationException("HttpServletRequest." + method);
        });
        FilterChain chain = (req, res) -> result.chainCalls++;

        new JwtAuthenticationFilter(jwt, users).doFilterInternal(request, null, chain);

        result.authentication = SecurityContextHolder.getContext().getAuthentication();
        return result;
    }

    @Test
    void aValidTokenForAnAccountWithoutACutoffAuthenticates() throws Exception {
        Run run = run(jwt(true, 1_000L), true, null, "Bearer abc");

        assertThat(run.authentication).isNotNull();
        assertThat(run.authentication.getName()).isEqualTo("jan@gmail.com");
        assertThat(run.chainCalls).isEqualTo(1);
        assertThat(run.lookups).containsExactly("jan@gmail.com"); // jedno zapytanie na żądanie
    }

    @Test
    void aTokenIssuedBeforeThePasswordChangeDoesNotAuthenticate() throws Exception {
        Run run = run(jwt(true, 1_000L), true, 2_000L, "Bearer abc");

        assertThat(run.authentication).isNull();
        assertThat(run.chainCalls).isEqualTo(1); // żądanie idzie dalej i dostaje 401 z konfiguracji bezpieczeństwa
    }

    @Test
    void aTokenIssuedAfterThePasswordChangeAuthenticates() throws Exception {
        Run run = run(jwt(true, 2_000L), true, 2_000L, "Bearer abc");

        assertThat(run.authentication).isNotNull();
    }

    @Test
    void aDeletedAccountDoesNotAuthenticateEvenWithAValidToken() throws Exception {
        Run run = run(jwt(true, 1_000L), false, null, "Bearer abc");

        assertThat(run.authentication).isNull();
    }

    @Test
    void anInvalidSignatureNeverReachesTheDatabase() throws Exception {
        Run run = run(jwt(false, 1_000L), true, null, "Bearer abc");

        assertThat(run.authentication).isNull();
        assertThat(run.lookups).isEmpty();
    }

    @Test
    void aRequestWithoutABearerTokenIsLeftAlone() throws Exception {
        Run run = run(jwt(true, 1_000L), true, null, null);

        assertThat(run.authentication).isNull();
        assertThat(run.lookups).isEmpty();
        assertThat(run.chainCalls).isEqualTo(1);
    }
}
