package pl.tutlaw.backend.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tutlaw.backend.auth.RefreshRules.Verdict;
import pl.tutlaw.backend.auth.dto.AuthResponse;
import pl.tutlaw.backend.user.User;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Tokeny odświeżające: wystawianie, wymiana (rotacja), wykrywanie kradzieży i unieważnianie.
 * <p>
 * Klient dostaje losowy token (256 bitów), a w bazie zostaje tylko jego skrót. Każde odświeżenie zużywa token i
 * wystawia następny z tej samej rodziny (jedno logowanie na jednym urządzeniu), więc ważny jest zawsze ten ostatni.
 * Gdy ktoś pokaże token już zużyty (poza krótkim oknem {@link RefreshRules#REUSE_GRACE}), zakładamy, że komuś
 * wyciekła kopia, i unieważniamy całą rodzinę: zalogowany musi wtedy podać hasło ponownie.
 */
@Service
@Slf4j
public class RefreshTokenService {

    /** Wiersze wygasłe dawniej niż tyle dni kasujemy przy okazji wystawiania nowego tokenu tego konta. */
    static final int KEEP_EXPIRED_DAYS = 7;

    private final RefreshTokenRepository repository;
    private final JwtService jwtService;
    private final Clock clock;
    private final Duration ttl;

    public RefreshTokenService(
            RefreshTokenRepository repository,
            JwtService jwtService,
            Clock clock,
            @Value("${app.jwt.refresh-token-expiration-days:30}") long ttlDays
    ) {
        this.repository = repository;
        this.jwtService = jwtService;
        this.clock = clock;
        this.ttl = Duration.ofDays(ttlDays);
    }

    /** Nowa sesja (nowa rodzina): po zalogowaniu i po zmianie hasła. */
    @Transactional
    public AuthResponse startSession(User user) {
        LocalDateTime now = LocalDateTime.now(clock);
        repository.deleteExpiredBefore(user.getId(), now.minusDays(KEEP_EXPIRED_DAYS));
        String refresh = store(user, UUID.randomUUID().toString(), now);
        return new AuthResponse(jwtService.generateToken(user.getPrivateEmail(), user.getRole()), refresh);
    }

    /**
     * Wymienia token na nową parę. Puste, gdy token jest nieznany, wygasły, unieważniony, zużyty dawniej niż
     * {@link RefreshRules#REUSE_GRACE} temu (wtedy dodatkowo unieważniamy rodzinę) albo konto nie jest już aktywne.
     * Zwracamy puste zamiast rzucać wyjątek, żeby unieważnienie rodziny zostało zapisane (wyjątek cofnąłby transakcję).
     */
    @Transactional
    public Optional<AuthResponse> refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        Optional<RefreshToken> found = repository.findByTokenHash(TokenCodec.sha256Hex(rawToken.trim()));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        RefreshToken token = found.get();
        LocalDateTime now = LocalDateTime.now(clock);

        Verdict verdict = RefreshRules.judge(token.getRotatedAt(), token.getRevokedAt(), token.getExpiresAt(), now);
        switch (verdict) {
            case REUSED -> {
                repository.revokeFamily(token.getFamilyId(), now);
                log.warn("Refresh token reuse detected for user {}: the whole session family was revoked",
                        token.getUser().getId());
                return Optional.empty();
            }
            case EXPIRED, REVOKED -> {
                return Optional.empty();
            }
            default -> {
                // OK albo REUSED_IN_GRACE: przechodzimy do wystawienia następnego tokenu.
            }
        }

        User user = token.getUser();
        if (!user.isActive()) {
            return Optional.empty();
        }
        if (verdict == Verdict.OK) {
            token.setRotatedAt(now);
            repository.save(token);
        }
        String refresh = store(user, token.getFamilyId(), now);
        return Optional.of(new AuthResponse(jwtService.generateToken(user.getPrivateEmail(), user.getRole()), refresh));
    }

    /** Wylogowanie: unieważnia całą sesję (rodzinę) tego tokenu. Nieznany token jest ignorowany. */
    @Transactional
    public void revokeSession(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findByTokenHash(TokenCodec.sha256Hex(rawToken.trim()))
                .ifPresent(token -> repository.revokeFamily(token.getFamilyId(), LocalDateTime.now(clock)));
    }

    /** Zmiana i reset hasła: wszystkie sesje konta przestają się odświeżać. */
    @Transactional
    public void revokeAll(User user) {
        repository.revokeAllForUser(user.getId(), LocalDateTime.now(clock));
    }

    private String store(User user, String familyId, LocalDateTime now) {
        String raw = TokenCodec.newToken();
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(TokenCodec.sha256Hex(raw));
        token.setFamilyId(familyId);
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(ttl));
        repository.save(token);
        return raw;
    }
}
