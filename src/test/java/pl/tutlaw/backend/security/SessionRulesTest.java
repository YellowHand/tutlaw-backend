package pl.tutlaw.backend.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionRulesTest {

    @Test
    void anAccountWithoutACutoffAcceptsEveryToken() {
        assertThat(SessionRules.isSessionValid(1_000L, null)).isTrue();
        assertThat(SessionRules.isSessionValid(null, null)).isTrue();
    }

    @Test
    void tokensIssuedBeforeTheCutoffAreRejected() {
        assertThat(SessionRules.isSessionValid(1_000L, 1_001L)).isFalse();
    }

    @Test
    void tokensIssuedAtOrAfterTheCutoffAreAccepted() {
        assertThat(SessionRules.isSessionValid(1_001L, 1_001L)).isTrue(); // świeży token z odpowiedzi na zmianę hasła
        assertThat(SessionRules.isSessionValid(5_000L, 1_001L)).isTrue();
    }

    @Test
    void aTokenWithoutATimestampCannotProveItIsNewerThanTheCutoff() {
        assertThat(SessionRules.isSessionValid(null, 1_001L)).isFalse();
    }
}
