package pl.gradusiuris.backend.common.config;

import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.protocol.Request;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import pl.gradusiuris.backend.common.exception.ConflictException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SentryConfigTest {

    @AfterEach
    void clean() {
        SecurityContextHolder.clearContext();
    }

    private final io.sentry.SentryOptions.BeforeSendCallback beforeSend = new SentryConfig().sentryBeforeSend();

    @Test
    void ordinaryClientErrorsAreDropped() {
        SentryEvent event = new SentryEvent(new ConflictException("duplikat"));

        assertThat(beforeSend.execute(event, new Hint())).isNull();
    }

    @Test
    void unexpectedErrorsAreKept() {
        SentryEvent event = new SentryEvent(new NullPointerException("błąd w kodzie"));

        assertThat(beforeSend.execute(event, new Hint())).isSameAs(event);
    }

    @Test
    void eventsWithoutAnExceptionAreKept_forExampleAnErrorLogLine() {
        SentryEvent event = new SentryEvent();

        assertThat(beforeSend.execute(event, new Hint())).isSameAs(event);
    }

    @Test
    void theQueryStringWithATokenNeverLeavesTheServer() {
        Request request = new Request();
        request.setUrl("https://api.gradusiuris.pl/api/auth/verify?token=TAJNY-TOKEN");
        request.setQueryString("token=TAJNY-TOKEN");
        request.setData("{\"password\":\"haslo\"}");
        request.setCookies("sid=abc");
        SentryEvent event = new SentryEvent(new NullPointerException());
        event.setRequest(request);

        SentryEvent sent = beforeSend.execute(event, new Hint());

        assertThat(sent).isNotNull();
        assertThat(sent.getRequest().getUrl()).isEqualTo("https://api.gradusiuris.pl/api/auth/verify");
        assertThat(sent.getRequest().getQueryString()).isNull();
        assertThat(sent.getRequest().getData()).isNull();
        assertThat(sent.getRequest().getCookies()).isNull();
    }

    @Test
    void withoutQueryCutsTheQueryAndTheFragment() {
        assertThat(SentryConfig.withoutQuery("https://a.pl/x")).isEqualTo("https://a.pl/x");
        assertThat(SentryConfig.withoutQuery("https://a.pl/x?t=1")).isEqualTo("https://a.pl/x");
        assertThat(SentryConfig.withoutQuery("https://a.pl/x#f?t=1")).isEqualTo("https://a.pl/x");
    }

    @Test
    void aLoggedInUserIsIdentifiedOnlyByTheAccountNumber() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("jan@gmail.com", null, List.of());
        auth.setDetails(5L);
        SecurityContextHolder.getContext().setAuthentication(auth);
        SentryEvent event = new SentryEvent(new NullPointerException());

        SentryEvent sent = beforeSend.execute(event, new Hint());

        assertThat(sent.getUser()).isNotNull();
        assertThat(sent.getUser().getId()).isEqualTo("5");
        assertThat(sent.getUser().getEmail()).isNull();
        assertThat(sent.getUser().getUsername()).isNull();
        assertThat(sent.getUser().getIpAddress()).isNull();
    }

    @Test
    void anAnonymousRequestHasNoUser() {
        SentryEvent event = new SentryEvent(new NullPointerException());

        assertThat(beforeSend.execute(event, new Hint()).getUser()).isNull();
    }
}
