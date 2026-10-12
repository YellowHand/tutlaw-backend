package pl.tutlaw.backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.security.crypto.password.PasswordEncoder;
import pl.tutlaw.backend.auth.dto.AuthResponse;
import pl.tutlaw.backend.auth.dto.LoginRequest;
import pl.tutlaw.backend.auth.dto.RegisterRequest;
import pl.tutlaw.backend.testsupport.FakeRepos;
import pl.tutlaw.backend.testsupport.MutableClock;
import pl.tutlaw.backend.user.Role;
import pl.tutlaw.backend.user.User;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cały przepływ kont na prawdziwym {@link AuthService}: rejestracja, aktywacja, logowanie, ponowne wysłanie linku,
 * reset hasła. Baza i poczta są "w pamięci" ({@link FakeRepos}), więc test nie potrzebuje Springa ani Dockera.
 */
class AuthFlowTest {

    /** Świeże repozytoria, zegar i serwis. Każdy test buduje własny zestaw. */
    private static final class Fixture {
        final MutableClock clock = new MutableClock(Instant.parse("2026-10-12T08:00:00Z"), ZoneId.of("Europe/Warsaw"));
        final List<User> users = new ArrayList<>();
        final List<EmailVerificationToken> verificationTokens = new ArrayList<>();
        final List<PasswordResetToken> resetTokens = new ArrayList<>();
        final FakeRepos.Mailbox mailbox = new FakeRepos.Mailbox();
        int dummyChecks = 0;

        final PasswordEncoder encoder = new PasswordEncoder() {
            @Override
            public String encode(CharSequence raw) {
                return "hash:" + raw;
            }

            @Override
            public boolean matches(CharSequence raw, String encoded) {
                dummyChecks++;
                return encoded != null && encoded.equals("hash:" + raw);
            }
        };

        final JwtService jwt = new JwtService("0123456789abcdef0123456789abcdef-test-secret", 60_000) {
            @Override
            public String generateToken(String subjectEmail, Role role) {
                return "jwt:" + subjectEmail + ":" + role;
            }
        };

        final AuthService service = new AuthService(
                FakeRepos.users(users),
                FakeRepos.verificationTokens(verificationTokens),
                FakeRepos.resetTokens(resetTokens),
                encoder,
                jwt,
                new EmailService(mailbox.sender(), "https://tutlaw.example/"),
                clock);

        LocalDateTime now() {
            return LocalDateTime.now(clock);
        }

        void register(String privateEmail, String universityEmail, String password) {
            service.register(new RegisterRequest(privateEmail, universityEmail, password, true));
        }

        /** Rejestruje i od razu aktywuje konto linkiem z maila. */
        void registerAndActivate(String privateEmail, String universityEmail, String password) {
            register(privateEmail, universityEmail, password);
            service.verify(verificationTokens.get(verificationTokens.size() - 1).getToken());
        }

        String tokenFromLastMail() {
            Matcher m = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(mailbox.last().getText());
            assertThat(m.find()).isTrue();
            return m.group(1);
        }
    }

    // ---------------------------------------------------------------- rejestracja

    @Test
    void registerNormalizesEmailsStoresTermsAcceptanceAndSendsActivationMail() {
        Fixture f = new Fixture();

        f.register("  Jan.Kowalski@Gmail.com ", "JAN@Stud.UG.edu.pl", "haslo1234");

        assertThat(f.users).hasSize(1);
        User user = f.users.get(0);
        assertThat(user.getPrivateEmail()).isEqualTo("jan.kowalski@gmail.com");
        assertThat(user.getUniversityEmail()).isEqualTo("jan@stud.ug.edu.pl");
        assertThat(user.isActive()).isFalse();
        assertThat(user.getTermsAcceptedAt()).isEqualTo(f.now());
        assertThat(f.mailbox.sent).hasSize(1);
        assertThat(f.mailbox.last().getTo()[0]).isEqualTo("jan@stud.ug.edu.pl");
        assertThat(f.verificationTokens).hasSize(1);
    }

    @Test
    void registerRequiresTermsAcceptance() {
        Fixture f = new Fixture();

        assertThatThrownBy(() -> f.service.register(new RegisterRequest("a@gmail.com", "a@uni.pl", "haslo1234", false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("regulaminu");
        assertThatThrownBy(() -> f.service.register(new RegisterRequest("a@gmail.com", "a@uni.pl", "haslo1234", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(f.users).isEmpty();
        assertThat(f.mailbox.sent).isEmpty();
    }

    @Test
    void registerRejectsDuplicatesIgnoringCase() {
        Fixture f = new Fixture();
        f.register("jan@gmail.com", "jan@uni.pl", "haslo1234");

        assertThatThrownBy(() -> f.register("JAN@gmail.com", "inny@uni.pl", "haslo1234"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("już istnieje");
        assertThatThrownBy(() -> f.register("inny@gmail.com", "Jan@Uni.pl", "haslo1234"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("uczelniany");
        assertThat(f.users).hasSize(1);
    }

    @Test
    void registerRejectsTooShortAndTooLongPasswords() {
        Fixture f = new Fixture();

        assertThatThrownBy(() -> f.register("a@gmail.com", "a@uni.pl", "krotkie"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("co najmniej 8");
        // 40 polskich liter to 80 bajtów w UTF-8, czyli więcej niż pojmie BCrypt.
        String tooLong = "ł".repeat(40);
        assertThatThrownBy(() -> f.register("a@gmail.com", "a@uni.pl", tooLong))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("za długie");
        assertThat(f.users).isEmpty();
    }

    // ---------------------------------------------------------------- aktywacja i logowanie

    @Test
    void loginIsBlockedUntilAccountIsActivated() {
        Fixture f = new Fixture();
        f.register("jan@gmail.com", "jan@uni.pl", "haslo1234");

        assertThatThrownBy(() -> f.service.login(new LoginRequest("jan@gmail.com", "haslo1234")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nie zostało jeszcze aktywowane");

        f.service.verify(f.verificationTokens.get(0).getToken());
        AuthResponse response = f.service.login(new LoginRequest("jan@gmail.com", "haslo1234"));
        assertThat(response.accessToken()).isEqualTo("jwt:jan@gmail.com:STUDENT");
    }

    @Test
    void loginIgnoresEmailCaseAndSurroundingSpaces() {
        Fixture f = new Fixture();
        f.registerAndActivate("jan@gmail.com", "jan@uni.pl", "haslo1234");

        AuthResponse response = f.service.login(new LoginRequest("  JAN@Gmail.COM ", "haslo1234"));

        assertThat(response.accessToken()).isEqualTo("jwt:jan@gmail.com:STUDENT");
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameErrorAndCostTheSameWork() {
        Fixture f = new Fixture();
        f.registerAndActivate("jan@gmail.com", "jan@uni.pl", "haslo1234");

        f.dummyChecks = 0;
        assertThatThrownBy(() -> f.service.login(new LoginRequest("jan@gmail.com", "zlehaslo99")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(AuthService.INVALID_CREDENTIALS);
        int checksForWrongPassword = f.dummyChecks;

        f.dummyChecks = 0;
        assertThatThrownBy(() -> f.service.login(new LoginRequest("nikt@gmail.com", "haslo1234")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(AuthService.INVALID_CREDENTIALS);
        // Dla nieznanego adresu też porównujemy hasło (z atrapą), żeby czas odpowiedzi niczego nie zdradzał.
        assertThat(f.dummyChecks).isEqualTo(checksForWrongPassword);
    }

    @Test
    void verifyIsIdempotentButRejectsExpiredLinks() {
        Fixture f = new Fixture();
        f.register("jan@gmail.com", "jan@uni.pl", "haslo1234");
        String token = f.verificationTokens.get(0).getToken();

        f.service.verify(token);
        f.service.verify(token); // drugie kliknięcie w ten sam link nie jest błędem

        Fixture g = new Fixture();
        g.register("ola@gmail.com", "ola@uni.pl", "haslo1234");
        String expired = g.verificationTokens.get(0).getToken();
        g.clock.advance(Duration.ofHours(25));
        assertThatThrownBy(() -> g.service.verify(expired))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wygasł");
        assertThatThrownBy(() -> g.service.verify("zmyslony-token"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------------------------------------------------------------- ponowne wysłanie linku aktywacyjnego

    @Test
    void resendVerificationSendsNewLinkOnlyForInactiveAccountsAndRespectsCooldown() {
        Fixture f = new Fixture();
        f.register("jan@gmail.com", "jan@uni.pl", "haslo1234");
        assertThat(f.mailbox.sent).hasSize(1);

        f.service.resendVerification("jan@gmail.com"); // tuż po rejestracji: cisza
        assertThat(f.mailbox.sent).hasSize(1);

        f.clock.advance(AuthService.EMAIL_COOLDOWN.plusSeconds(1));
        f.service.resendVerification("JAN@gmail.com");
        assertThat(f.mailbox.sent).hasSize(2);
        assertThat(f.mailbox.last().getTo()[0]).isEqualTo("jan@uni.pl");
        assertThat(f.verificationTokens).hasSize(2);

        // Nowy link działa.
        f.service.verify(f.verificationTokens.get(1).getToken());
        // Konto aktywne: nic więcej nie wysyłamy.
        f.clock.advance(Duration.ofHours(1));
        f.service.resendVerification("jan@gmail.com");
        assertThat(f.mailbox.sent).hasSize(2);
    }

    @Test
    void resendVerificationForUnknownAddressDoesNothingAndDoesNotFail() {
        Fixture f = new Fixture();

        f.service.resendVerification("nikt@gmail.com");

        assertThat(f.mailbox.sent).isEmpty();
        assertThat(f.verificationTokens).isEmpty();
    }

    @Test
    void resendVerificationSwallowsMailFailuresSoTheAnswerStaysTheSame() {
        Fixture f = new Fixture();
        f.register("jan@gmail.com", "jan@uni.pl", "haslo1234");
        f.clock.advance(AuthService.EMAIL_COOLDOWN.plusSeconds(1));
        f.mailbox.failing = true;

        f.service.resendVerification("jan@gmail.com"); // bez wyjątku
        assertThat(f.verificationTokens).hasSize(2);
    }

    // ---------------------------------------------------------------- reset hasła

    @Test
    void forgotPasswordSendsOneTimeLinkToUniversityAddressAndStoresOnlyItsHash() {
        Fixture f = new Fixture();
        f.registerAndActivate("jan@gmail.com", "jan@uni.pl", "haslo1234");
        f.mailbox.sent.clear();

        f.service.forgotPassword("Jan@Gmail.com");

        assertThat(f.mailbox.sent).hasSize(1);
        SimpleMailMessage mail = f.mailbox.last();
        assertThat(mail.getTo()[0]).isEqualTo("jan@uni.pl");
        assertThat(mail.getText()).contains("https://tutlaw.example/reset-password?token=");
        String rawToken = f.tokenFromLastMail();
        assertThat(f.resetTokens).hasSize(1);
        PasswordResetToken stored = f.resetTokens.get(0);
        assertThat(stored.getTokenHash()).isEqualTo(TokenCodec.sha256Hex(rawToken));
        assertThat(stored.getTokenHash()).isNotEqualTo(rawToken);
        assertThat(stored.getExpiresAt()).isEqualTo(f.now().plus(AuthService.RESET_TTL));
    }

    @Test
    void forgotPasswordDoesNothingForUnknownOrInactiveAccountsAndDuringCooldown() {
        Fixture f = new Fixture();
        f.register("jan@gmail.com", "jan@uni.pl", "haslo1234"); // niezaktywowane
        f.mailbox.sent.clear();

        f.service.forgotPassword("jan@gmail.com");
        f.service.forgotPassword("nikt@gmail.com");
        assertThat(f.mailbox.sent).isEmpty();
        assertThat(f.resetTokens).isEmpty();

        f.service.verify(f.verificationTokens.get(0).getToken());
        f.service.forgotPassword("jan@gmail.com");
        f.service.forgotPassword("jan@gmail.com"); // drugi raz od razu: cisza
        assertThat(f.mailbox.sent).hasSize(1);

        f.clock.advance(AuthService.EMAIL_COOLDOWN.plusSeconds(1));
        f.service.forgotPassword("jan@gmail.com");
        assertThat(f.mailbox.sent).hasSize(2);
    }

    @Test
    void forgotPasswordSwallowsMailFailures() {
        Fixture f = new Fixture();
        f.registerAndActivate("jan@gmail.com", "jan@uni.pl", "haslo1234");
        f.mailbox.failing = true;

        f.service.forgotPassword("jan@gmail.com"); // bez wyjątku

        assertThat(f.resetTokens).hasSize(1);
    }

    @Test
    void resetPasswordChangesPasswordAndTheLinkWorksOnlyOnce() {
        Fixture f = new Fixture();
        f.registerAndActivate("jan@gmail.com", "jan@uni.pl", "haslo1234");
        f.service.forgotPassword("jan@gmail.com");
        String rawToken = f.tokenFromLastMail();

        f.service.resetPassword(rawToken, "nowehaslo99");

        assertThat(f.service.login(new LoginRequest("jan@gmail.com", "nowehaslo99")).accessToken()).isNotNull();
        assertThatThrownBy(() -> f.service.login(new LoginRequest("jan@gmail.com", "haslo1234")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> f.service.resetPassword(rawToken, "jeszczeinne99"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(AuthService.INVALID_RESET_LINK);
    }

    @Test
    void resetPasswordInvalidatesOtherOpenLinks() {
        Fixture f = new Fixture();
        f.registerAndActivate("jan@gmail.com", "jan@uni.pl", "haslo1234");
        f.service.forgotPassword("jan@gmail.com");
        String firstToken = f.tokenFromLastMail();
        f.clock.advance(AuthService.EMAIL_COOLDOWN.plusSeconds(1));
        f.service.forgotPassword("jan@gmail.com");
        String secondToken = f.tokenFromLastMail();
        assertThat(secondToken).isNotEqualTo(firstToken);

        f.service.resetPassword(secondToken, "nowehaslo99");

        assertThatThrownBy(() -> f.service.resetPassword(firstToken, "kolejnehaslo1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resetPasswordRejectsExpiredUnknownAndWeakPasswordsWithoutBurningTheLink() {
        Fixture f = new Fixture();
        f.registerAndActivate("jan@gmail.com", "jan@uni.pl", "haslo1234");
        f.service.forgotPassword("jan@gmail.com");
        String rawToken = f.tokenFromLastMail();

        assertThatThrownBy(() -> f.service.resetPassword("zmyslony", "nowehaslo99"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(AuthService.INVALID_RESET_LINK);
        assertThatThrownBy(() -> f.service.resetPassword(rawToken, "krotkie"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("co najmniej 8");
        // Słabe hasło nie zużywa linku: da się jeszcze raz.
        f.service.resetPassword(" " + rawToken + " ", "nowehaslo99");

        Fixture g = new Fixture();
        g.registerAndActivate("ola@gmail.com", "ola@uni.pl", "haslo1234");
        g.service.forgotPassword("ola@gmail.com");
        String expiredToken = g.tokenFromLastMail();
        g.clock.advance(AuthService.RESET_TTL.plusMinutes(1));
        assertThatThrownBy(() -> g.service.resetPassword(expiredToken, "nowehaslo99"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(AuthService.INVALID_RESET_LINK);
    }
}
