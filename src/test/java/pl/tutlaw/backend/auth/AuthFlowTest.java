package pl.tutlaw.backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.security.crypto.password.PasswordEncoder;
import pl.tutlaw.backend.auth.dto.AuthResponse;
import pl.tutlaw.backend.auth.dto.LoginRequest;
import pl.tutlaw.backend.auth.dto.RegisterRequest;
import pl.tutlaw.backend.testsupport.FakeRepos;
import pl.tutlaw.backend.common.exception.UnauthorizedException;
import pl.tutlaw.backend.testsupport.MutableClock;
import pl.tutlaw.backend.user.Role;
import pl.tutlaw.backend.user.User;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
        int encodes = 0;

        final PasswordEncoder encoder = new PasswordEncoder() {
            @Override
            public String encode(CharSequence raw) {
                encodes++;
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
                new AfterCommitMailer(Runnable::run),
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
    void registerForAnExistingAddressLooksLikeSuccessAndChangesNothing() {
        Fixture f = new Fixture();
        f.register("jan@gmail.com", "jan@uni.pl", "haslo1234");
        f.mailbox.sent.clear();

        // Ten sam adres prywatny (inna wielkość liter), potem ten sam uczelniany: żadnego wyjątku, żadnej poczty.
        f.register("JAN@gmail.com", "inny@uni.pl", "innehaslo99");
        f.register("inny@gmail.com", "Jan@Uni.pl", "innehaslo99");

        assertThat(f.users).hasSize(1);
        assertThat(f.users.get(0).getPasswordHash()).isEqualTo("hash:haslo1234");
        assertThat(f.verificationTokens).hasSize(1);
        assertThat(f.mailbox.sent).isEmpty();
    }

    @Test
    void registerHashesThePasswordEvenWhenTheAccountExists_soTheTimingDoesNotGiveItAway() {
        Fixture f = new Fixture();
        f.register("jan@gmail.com", "jan@uni.pl", "haslo1234");

        f.encodes = 0;
        f.register("jan@gmail.com", "jan@uni.pl", "haslo1234");
        int encodesForExisting = f.encodes;
        f.encodes = 0;
        f.register("nowy@gmail.com", "nowy@uni.pl", "haslo1234");

        assertThat(encodesForExisting).isEqualTo(1);
        assertThat(f.encodes).isEqualTo(encodesForExisting);
    }

    @Test
    void registerStillRejectsTermsAndPasswordProblemsForExistingAddressesToo() {
        Fixture f = new Fixture();
        f.register("jan@gmail.com", "jan@uni.pl", "haslo1234");

        // Te błędy nie zależą od tego, czy konto istnieje, więc nie zdradzają niczego i nadal są zgłaszane.
        assertThatThrownBy(() -> f.register("jan@gmail.com", "jan@uni.pl", "krotkie"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("co najmniej 8");
    }

    @Test
    void registerSendsTheActivationMailOnlyAfterCommit() {
        Fixture f = new Fixture();

        TransactionSynchronizationManager.initSynchronization();
        try {
            f.register("jan@gmail.com", "jan@uni.pl", "haslo1234");
            assertThat(f.mailbox.sent).isEmpty(); // transakcja jeszcze trwa

            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertThat(f.mailbox.sent).hasSize(1);
        assertThat(f.mailbox.last().getTo()[0]).isEqualTo("jan@uni.pl");
    }

    @Test
    void registerSendsNothingWhenTheTransactionRollsBack() {
        Fixture f = new Fixture();

        TransactionSynchronizationManager.initSynchronization();
        try {
            f.register("jan@gmail.com", "jan@uni.pl", "haslo1234");
            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK); // bez afterCommit
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertThat(f.mailbox.sent).isEmpty();
    }

    @Test
    void registerSucceedsEvenWhenTheMailServerIsDown() {
        Fixture f = new Fixture();
        f.mailbox.failing = true;

        f.register("jan@gmail.com", "jan@uni.pl", "haslo1234"); // wcześniej kończyło się błędem 500 i wycofaniem konta

        assertThat(f.users).hasSize(1);
        assertThat(f.verificationTokens).hasSize(1);
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

    // ---------------------------------------------------------------- zmiana hasła z konta i unieważnianie sesji

    private long nowEpochSeconds(Fixture f) {
        return f.clock.instant().getEpochSecond();
    }

    @Test
    void changePasswordSetsNewHashInvalidatesOldSessionsAndReturnsAFreshToken() {
        Fixture f = new Fixture();
        f.registerAndActivate("jan@gmail.com", "jan@uni.pl", "haslo1234");
        f.mailbox.sent.clear();
        User user = f.users.get(0);
        assertThat(user.getTokensValidFrom()).isNull();

        AuthResponse response = f.service.changePassword("jan@gmail.com", "haslo1234", "nowehaslo99");

        assertThat(response.accessToken()).isEqualTo("jwt:jan@gmail.com:STUDENT");
        assertThat(user.getPasswordHash()).isEqualTo("hash:nowehaslo99");
        assertThat(user.getTokensValidFrom()).isEqualTo(nowEpochSeconds(f));
        assertThatThrownBy(() -> f.service.login(new LoginRequest("jan@gmail.com", "haslo1234")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(f.service.login(new LoginRequest("jan@gmail.com", "nowehaslo99")).accessToken()).isNotNull();
    }

    @Test
    void changePasswordNotifiesTheUniversityAddressWithoutAnyToken() {
        Fixture f = new Fixture();
        f.registerAndActivate("jan@gmail.com", "jan@uni.pl", "haslo1234");
        f.mailbox.sent.clear();

        f.service.changePassword("jan@gmail.com", "haslo1234", "nowehaslo99");

        assertThat(f.mailbox.sent).hasSize(1);
        SimpleMailMessage mail = f.mailbox.last();
        assertThat(mail.getTo()[0]).isEqualTo("jan@uni.pl");
        assertThat(mail.getSubject()).contains("zostało zmienione");
        assertThat(mail.getText()).contains("https://tutlaw.example/forgot-password");
        assertThat(mail.getText()).doesNotContain("token=").doesNotContain("nowehaslo99").doesNotContain("haslo1234");
    }

    @Test
    void changePasswordClosesOpenResetLinks() {
        Fixture f = new Fixture();
        f.registerAndActivate("jan@gmail.com", "jan@uni.pl", "haslo1234");
        f.service.forgotPassword("jan@gmail.com");
        String resetToken = f.tokenFromLastMail();

        f.service.changePassword("jan@gmail.com", "haslo1234", "nowehaslo99");

        assertThatThrownBy(() -> f.service.resetPassword(resetToken, "jeszczeinne99"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(AuthService.INVALID_RESET_LINK);
    }

    @Test
    void changePasswordWithAWrongCurrentPasswordIsRefusedAndChangesNothing() {
        Fixture f = new Fixture();
        f.registerAndActivate("jan@gmail.com", "jan@uni.pl", "haslo1234");
        f.mailbox.sent.clear();

        assertThatThrownBy(() -> f.service.changePassword("jan@gmail.com", "zlehaslo99", "nowehaslo99"))
                .isInstanceOf(WrongPasswordException.class)
                .hasMessageContaining("Obecne hasło");

        User user = f.users.get(0);
        assertThat(user.getPasswordHash()).isEqualTo("hash:haslo1234");
        assertThat(user.getTokensValidFrom()).isNull();
        assertThat(f.mailbox.sent).isEmpty();
    }

    @Test
    void changePasswordRejectsWeakAndUnchangedPasswords_withoutCountingThemAsWrongGuesses() {
        Fixture f = new Fixture();
        f.registerAndActivate("jan@gmail.com", "jan@uni.pl", "haslo1234");

        assertThatThrownBy(() -> f.service.changePassword("jan@gmail.com", "haslo1234", "krotkie"))
                .isInstanceOf(IllegalArgumentException.class)
                .isNotInstanceOf(WrongPasswordException.class)
                .hasMessageContaining("co najmniej 8");
        assertThatThrownBy(() -> f.service.changePassword("jan@gmail.com", "haslo1234", "haslo1234"))
                .isInstanceOf(IllegalArgumentException.class)
                .isNotInstanceOf(WrongPasswordException.class)
                .hasMessageContaining("inne niż obecne");

        assertThat(f.users.get(0).getPasswordHash()).isEqualTo("hash:haslo1234");
        assertThat(f.users.get(0).getTokensValidFrom()).isNull();
    }

    @Test
    void changePasswordForAnUnknownAccountIsUnauthorized() {
        Fixture f = new Fixture();

        assertThatThrownBy(() -> f.service.changePassword("nikt@gmail.com", "haslo1234", "nowehaslo99"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void changePasswordSucceedsEvenWhenTheNotificationCannotBeSent() {
        Fixture f = new Fixture();
        f.registerAndActivate("jan@gmail.com", "jan@uni.pl", "haslo1234");
        f.mailbox.failing = true;

        f.service.changePassword("jan@gmail.com", "haslo1234", "nowehaslo99"); // bez wyjątku

        assertThat(f.users.get(0).getPasswordHash()).isEqualTo("hash:nowehaslo99");
    }

    @Test
    void resetPasswordAlsoInvalidatesExistingSessions() {
        Fixture f = new Fixture();
        f.registerAndActivate("jan@gmail.com", "jan@uni.pl", "haslo1234");
        f.service.forgotPassword("jan@gmail.com");
        String rawToken = f.tokenFromLastMail();
        f.clock.advance(Duration.ofMinutes(5));

        f.service.resetPassword(rawToken, "nowehaslo99");

        assertThat(f.users.get(0).getTokensValidFrom()).isEqualTo(nowEpochSeconds(f));
    }
}
