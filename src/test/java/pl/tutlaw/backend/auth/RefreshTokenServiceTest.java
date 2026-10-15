package pl.tutlaw.backend.auth;

import org.junit.jupiter.api.Test;
import pl.tutlaw.backend.auth.dto.AuthResponse;
import pl.tutlaw.backend.testsupport.FakeRepos;
import pl.tutlaw.backend.testsupport.MutableClock;
import pl.tutlaw.backend.user.Role;
import pl.tutlaw.backend.user.User;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Tokeny odświeżające na prawdziwym serwisie; baza jest "w pamięci" ({@link FakeRepos}). */
class RefreshTokenServiceTest {

    private static final class Fixture {
        final MutableClock clock = MutableClock.at("2026-10-12T08:00:00Z");
        final List<RefreshToken> rows = new ArrayList<>();
        int issued = 0;

        final JwtService jwt = new JwtService("0123456789abcdef0123456789abcdef-test-secret", 60_000) {
            @Override
            public String generateToken(String subjectEmail, Role role) {
                return "jwt:" + subjectEmail + ":" + (++issued);
            }
        };

        final RefreshTokenService service =
                new RefreshTokenService(FakeRepos.refreshTokens(rows), jwt, clock, 30);

        final User jan = user(1L, "jan@gmail.com", true);
        final User ola = user(2L, "ola@gmail.com", true);

        static User user(Long id, String email, boolean active) {
            User u = new User();
            u.setId(id);
            u.setPrivateEmail(email);
            u.setUniversityEmail("u-" + email);
            u.setActive(active);
            return u;
        }

        RefreshToken rowFor(String raw) {
            String hash = TokenCodec.sha256Hex(raw);
            return rows.stream().filter(r -> r.getTokenHash().equals(hash)).findFirst().orElseThrow();
        }
    }

    @Test
    void startingASessionReturnsBothTokensAndStoresOnlyTheHash() {
        Fixture f = new Fixture();

        AuthResponse pair = f.service.startSession(f.jan);

        assertThat(pair.accessToken()).startsWith("jwt:jan@gmail.com");
        assertThat(pair.refreshToken()).hasSize(43);
        assertThat(f.rows).hasSize(1);
        RefreshToken row = f.rows.get(0);
        assertThat(row.getTokenHash()).isEqualTo(TokenCodec.sha256Hex(pair.refreshToken())).hasSize(64);
        assertThat(row.getTokenHash()).isNotEqualTo(pair.refreshToken());
        assertThat(row.getExpiresAt()).isEqualTo(row.getCreatedAt().plusDays(30));
        assertThat(row.getRotatedAt()).isNull();
        assertThat(row.getRevokedAt()).isNull();
    }

    @Test
    void refreshingExchangesTheTokenForANewPairInTheSameFamily() {
        Fixture f = new Fixture();
        AuthResponse first = f.service.startSession(f.jan);
        f.clock.advance(Duration.ofMinutes(30));

        Optional<AuthResponse> second = f.service.refresh(first.refreshToken());

        assertThat(second).isPresent();
        assertThat(second.get().refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(second.get().accessToken()).isNotEqualTo(first.accessToken());
        assertThat(f.rows).hasSize(2);
        RefreshToken old = f.rowFor(first.refreshToken());
        RefreshToken fresh = f.rowFor(second.get().refreshToken());
        assertThat(old.getRotatedAt()).isNotNull();
        assertThat(fresh.getFamilyId()).isEqualTo(old.getFamilyId());
        assertThat(fresh.getExpiresAt()).isEqualTo(old.getExpiresAt().plusMinutes(30)); // ważność liczy się od ostatniego użycia
    }

    @Test
    void aChainOfRefreshesKeepsWorkingAsLongAsEachTokenIsUsedOnce() {
        Fixture f = new Fixture();
        AuthResponse pair = f.service.startSession(f.jan);

        for (int i = 0; i < 5; i++) {
            f.clock.advance(Duration.ofHours(12));
            pair = f.service.refresh(pair.refreshToken()).orElseThrow();
        }

        assertThat(f.rows).hasSize(6);
        assertThat(f.rows.stream().filter(r -> r.getRevokedAt() != null)).isEmpty();
    }

    @Test
    void anUnknownBlankOrNullTokenIsRefused() {
        Fixture f = new Fixture();
        f.service.startSession(f.jan);

        assertThat(f.service.refresh("nieznany")).isEmpty();
        assertThat(f.service.refresh("   ")).isEmpty();
        assertThat(f.service.refresh(null)).isEmpty();
    }

    @Test
    void anExpiredTokenIsRefused() {
        Fixture f = new Fixture();
        AuthResponse pair = f.service.startSession(f.jan);
        f.clock.advance(Duration.ofDays(31)); // z zapasem na zmianę czasu letniego/zimowego

        assertThat(f.service.refresh(pair.refreshToken())).isEmpty();
        assertThat(f.rows).hasSize(1);
    }

    @Test
    void reusingATokenAfterTheGraceWindowRevokesTheWholeFamily() {
        Fixture f = new Fixture();
        AuthResponse first = f.service.startSession(f.jan);
        f.clock.advance(Duration.ofMinutes(30));
        AuthResponse second = f.service.refresh(first.refreshToken()).orElseThrow();
        f.clock.advance(Duration.ofMinutes(30));

        // Ktoś pokazuje kopię pierwszego tokenu, który uprawniony użytkownik dawno wymienił.
        assertThat(f.service.refresh(first.refreshToken())).isEmpty();

        assertThat(f.rows).allSatisfy(r -> assertThat(r.getRevokedAt()).isNotNull());
        // Najnowszy token też przestał działać: i złodziej, i właściciel muszą się zalogować od nowa.
        assertThat(f.service.refresh(second.refreshToken())).isEmpty();
    }

    @Test
    void reuseInsideTheGraceWindowIssuesAnotherTokenAndRevokesNothing() {
        Fixture f = new Fixture();
        AuthResponse first = f.service.startSession(f.jan);
        f.clock.advance(Duration.ofMinutes(30));
        AuthResponse second = f.service.refresh(first.refreshToken()).orElseThrow();

        // Druga karta przeglądarki odświeżała w tej samej chwili tym samym tokenem.
        f.clock.advance(Duration.ofSeconds(4));
        Optional<AuthResponse> parallel = f.service.refresh(first.refreshToken());

        assertThat(parallel).isPresent();
        assertThat(parallel.get().refreshToken()).isNotEqualTo(second.refreshToken());
        assertThat(f.rows).hasSize(3);
        assertThat(f.rows.stream().filter(r -> r.getRevokedAt() != null)).isEmpty();
        assertThat(f.service.refresh(second.refreshToken())).isPresent();
    }

    @Test
    void reuseDoesNotTouchOtherSessionsOfTheSameAccount() {
        Fixture f = new Fixture();
        AuthResponse laptop = f.service.startSession(f.jan);
        AuthResponse phone = f.service.startSession(f.jan);
        f.clock.advance(Duration.ofMinutes(30));
        f.service.refresh(laptop.refreshToken()).orElseThrow();
        f.clock.advance(Duration.ofMinutes(1));

        assertThat(f.service.refresh(laptop.refreshToken())).isEmpty(); // kradzież na laptopie

        assertThat(f.service.refresh(phone.refreshToken())).isPresent(); // telefon działa dalej
    }

    @Test
    void revokingASessionStopsAllItsTokensButNotOtherSessions() {
        Fixture f = new Fixture();
        AuthResponse laptop = f.service.startSession(f.jan);
        AuthResponse phone = f.service.startSession(f.jan);
        f.clock.advance(Duration.ofMinutes(30));
        AuthResponse laptopNext = f.service.refresh(laptop.refreshToken()).orElseThrow();

        f.service.revokeSession(laptopNext.refreshToken());

        assertThat(f.service.refresh(laptopNext.refreshToken())).isEmpty();
        assertThat(f.service.refresh(phone.refreshToken())).isPresent();
    }

    @Test
    void revokingAnUnknownOrBlankTokenDoesNothing() {
        Fixture f = new Fixture();
        AuthResponse pair = f.service.startSession(f.jan);

        f.service.revokeSession("nieznany");
        f.service.revokeSession(null);
        f.service.revokeSession(" ");

        assertThat(f.service.refresh(pair.refreshToken())).isPresent();
    }

    @Test
    void revokingAllSessionsOfAnAccountLeavesOtherAccountsAlone() {
        Fixture f = new Fixture();
        AuthResponse laptop = f.service.startSession(f.jan);
        AuthResponse phone = f.service.startSession(f.jan);
        AuthResponse olas = f.service.startSession(f.ola);

        f.service.revokeAll(f.jan);

        assertThat(f.service.refresh(laptop.refreshToken())).isEmpty();
        assertThat(f.service.refresh(phone.refreshToken())).isEmpty();
        assertThat(f.service.refresh(olas.refreshToken())).isPresent();
    }

    @Test
    void anInactiveAccountCannotRefresh() {
        Fixture f = new Fixture();
        AuthResponse pair = f.service.startSession(f.jan);
        f.jan.setActive(false);

        assertThat(f.service.refresh(pair.refreshToken())).isEmpty();
    }

    @Test
    void startingASessionCleansUpLongExpiredRowsOfThatAccountOnly() {
        Fixture f = new Fixture();
        f.service.startSession(f.jan);
        f.service.startSession(f.ola);
        f.clock.advance(Duration.ofDays(30 + RefreshTokenService.KEEP_EXPIRED_DAYS + 1));

        f.service.startSession(f.jan);

        assertThat(f.rows).hasSize(2); // stary wiersz Jana skasowany, stary wiersz Oli zostaje, nowy Jana dodany
        assertThat(f.rows.stream().filter(r -> r.getUser().getId().equals(2L))).hasSize(1);
        assertThat(f.rows.stream().filter(r -> r.getUser().getId().equals(1L))).hasSize(1);
    }

    @Test
    void tokenWithSurroundingWhitespaceStillMatches() {
        Fixture f = new Fixture();
        AuthResponse pair = f.service.startSession(f.jan);

        assertThat(f.service.refresh("  " + pair.refreshToken() + "\n")).isPresent();
    }
}
