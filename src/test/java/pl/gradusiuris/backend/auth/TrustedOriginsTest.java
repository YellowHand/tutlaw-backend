package pl.gradusiuris.backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrustedOriginsTest {

    private final TrustedOrigins origins = new TrustedOrigins("https://gradusiuris.pl, https://www.gradusiuris.pl/");

    @Test
    void ourFrontendAddressesPass() {
        assertThatCode(() -> origins.requireTrusted("https://gradusiuris.pl")).doesNotThrowAnyException();
        assertThatCode(() -> origins.requireTrusted("https://www.gradusiuris.pl")).doesNotThrowAnyException();
    }

    @Test
    void aMissingOriginPassesBecauseBrowsersAlwaysSendItOnCrossSitePosts() {
        assertThatCode(() -> origins.requireTrusted(null)).doesNotThrowAnyException();
        assertThatCode(() -> origins.requireTrusted("  ")).doesNotThrowAnyException();
    }

    @Test
    void foreignAndLookAlikeOriginsAreRefusedWith403() {
        for (String origin : new String[]{"https://evil.example", "http://gradusiuris.pl", "https://gradusiuris.pl.evil.example",
                "https://api.gradusiuris.pl", "null"}) {
            assertThatThrownBy(() -> origins.requireTrusted(origin))
                    .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(403));
        }
    }
}
