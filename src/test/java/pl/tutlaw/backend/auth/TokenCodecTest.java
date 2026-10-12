package pl.tutlaw.backend.auth;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TokenCodecTest {

    @Test
    void tokensAreLongUrlSafeAndDifferentEveryTime() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String token = TokenCodec.newToken();
            assertThat(token.length()).isEqualTo(43);
            assertThat(token.matches("[A-Za-z0-9_-]+")).isTrue();
            seen.add(token);
        }
        assertThat(seen).hasSize(200);
    }

    @Test
    void sha256MatchesKnownTestVectors() {
        assertThat(TokenCodec.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(TokenCodec.sha256Hex(""))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }
}
