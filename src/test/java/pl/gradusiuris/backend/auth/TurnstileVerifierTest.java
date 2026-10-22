package pl.gradusiuris.backend.auth;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.gradusiuris.backend.common.exception.BotCheckException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Udajemy serwer Cloudflare lokalnym serwerem HTTP: test nie wychodzi do internetu. */
class TurnstileVerifierTest {

    private HttpServer server;
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String body = "{\"success\":true}";

    @BeforeEach
    void startFakeCloudflare() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/siteverify", exchange -> {
            calls.incrementAndGet();
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private TurnstileVerifier verifier(String secret) {
        return new TurnstileVerifier(secret, "http://127.0.0.1:" + server.getAddress().getPort() + "/siteverify", 2000);
    }

    @Test
    void withoutSecretTheCheckIsOffAndNothingIsSent() {
        TurnstileVerifier verifier = verifier("");

        assertThat(verifier.isEnabled()).isFalse();
        assertThatCode(() -> verifier.verify(null, "1.2.3.4")).doesNotThrowAnyException();
        assertThat(calls.get()).isZero();
    }

    @Test
    void acceptsATokenThatCloudflareConfirms() {
        assertThatCode(() -> verifier("sekret").verify("token-123", "203.0.113.7")).doesNotThrowAnyException();

        assertThat(calls.get()).isEqualTo(1);
        assertThat(lastBody.get()).contains("secret=sekret", "response=token-123", "remoteip=203.0.113.7");
    }

    @Test
    void rejectsATokenThatCloudflareDoesNotConfirm() {
        body = "{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}";

        assertThatThrownBy(() -> verifier("sekret").verify("zly-token", null))
                .isInstanceOfSatisfying(BotCheckException.class, e -> assertThat(e.getStatus()).isEqualTo(400));
    }

    @Test
    void missingBlankOrOversizedTokenIsRejectedWithoutAskingCloudflare() {
        TurnstileVerifier verifier = verifier("sekret");

        for (String token : new String[]{null, "", "   ", "x".repeat(TurnstileVerifier.MAX_TOKEN_LENGTH + 1)}) {
            assertThatThrownBy(() -> verifier.verify(token, "1.2.3.4"))
                    .isInstanceOfSatisfying(BotCheckException.class, e -> assertThat(e.getStatus()).isEqualTo(400));
        }
        assertThat(calls.get()).isZero();
    }

    @Test
    void tokenIsUrlEncodedSoItCannotInjectOtherFormFields() {
        verifier("sekret").verify("a&secret=podmiana", null);

        assertThat(lastBody.get()).contains("response=a%26secret%3Dpodmiana");
        assertThat(lastBody.get()).doesNotContain("remoteip");
    }

    @Test
    void failsClosedWhenCloudflareAnswersWithAnError() {
        status = 500;

        assertThatThrownBy(() -> verifier("sekret").verify("token", "1.2.3.4"))
                .isInstanceOfSatisfying(BotCheckException.class, e -> assertThat(e.getStatus()).isEqualTo(503));
    }

    @Test
    void failsClosedWhenCloudflareIsUnreachable() {
        int port = server.getAddress().getPort();
        server.stop(0);
        TurnstileVerifier verifier = new TurnstileVerifier("sekret", "http://127.0.0.1:" + port + "/siteverify", 1000);

        assertThatThrownBy(() -> verifier.verify("token", "1.2.3.4"))
                .isInstanceOfSatisfying(BotCheckException.class, e -> assertThat(e.getStatus()).isEqualTo(503));
    }
}
