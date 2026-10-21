package pl.gradusiuris.backend.auth;

import org.junit.jupiter.api.Test;
import pl.gradusiuris.backend.testsupport.FakeRepos;

import static org.assertj.core.api.Assertions.assertThat;

/** Linki w mailach powstają z adresu frontendu z konfiguracji (nigdy z wpisanego na sztywno), a nadawca z {@code app.mail.from}. */
class EmailServiceTest {

    @Test
    void verificationLinkUsesTheConfiguredFrontendAddress_withoutDoubleSlash() {
        FakeRepos.Mailbox mailbox = new FakeRepos.Mailbox();
        new EmailService(mailbox.sender(), "https://gradusiuris.pl/", "").sendVerificationEmail("jan@example.com", "abc123");

        assertThat(mailbox.last().getText()).contains("https://gradusiuris.pl/verify?token=abc123");
        assertThat(mailbox.last().getText()).doesNotContain("localhost").doesNotContain("//verify");
    }

    @Test
    void passwordLinksUseTheConfiguredFrontendAddress() {
        FakeRepos.Mailbox mailbox = new FakeRepos.Mailbox();
        EmailService service = new EmailService(mailbox.sender(), "https://gradusiuris.pl", "");

        service.sendPasswordResetEmail("jan@example.com", "tok");
        service.sendPasswordChangedEmail("jan@example.com");

        assertThat(mailbox.sent.get(0).getText()).contains("https://gradusiuris.pl/reset-password?token=tok");
        assertThat(mailbox.sent.get(1).getText()).contains("https://gradusiuris.pl/forgot-password");
    }

    @Test
    void senderIsSetOnEveryKindOfMail_whenConfigured() {
        FakeRepos.Mailbox mailbox = new FakeRepos.Mailbox();
        EmailService service = new EmailService(mailbox.sender(), "https://gradusiuris.pl", "  Gradus iuris <no-reply@gradusiuris.pl> ");

        service.sendVerificationEmail("a@example.com", "t1");
        service.sendPasswordResetEmail("a@example.com", "t2");
        service.sendPasswordChangedEmail("a@example.com");

        assertThat(mailbox.sent).hasSize(3);
        assertThat(mailbox.sent).allSatisfy(m -> assertThat(m.getFrom()).isEqualTo("Gradus iuris <no-reply@gradusiuris.pl>"));
    }

    @Test
    void senderIsLeftUnset_whenNotConfigured() {
        FakeRepos.Mailbox mailbox = new FakeRepos.Mailbox();
        new EmailService(mailbox.sender(), "http://localhost:5173").sendPasswordChangedEmail("a@example.com");

        assertThat(mailbox.last().getFrom()).isNull();
    }
}
