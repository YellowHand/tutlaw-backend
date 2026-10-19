package pl.tutlaw.backend.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tutlaw.backend.auth.dto.AuthResponse;
import pl.tutlaw.backend.auth.dto.LoginRequest;
import pl.tutlaw.backend.auth.dto.RegisterRequest;
import pl.tutlaw.backend.common.exception.UnauthorizedException;
import pl.tutlaw.backend.user.User;
import pl.tutlaw.backend.user.UserRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    static final Duration VERIFICATION_TTL = Duration.ofHours(24);
    static final Duration RESET_TTL = Duration.ofHours(1);
    /** Nie wysyłamy kolejnego maila (aktywacja, reset) częściej niż co tyle czasu. */
    static final Duration EMAIL_COOLDOWN = Duration.ofMinutes(2);

    static final String INVALID_CREDENTIALS = "Nieprawidłowy adres e-mail lub hasło";
    static final String NOT_VERIFIED =
            "Konto nie zostało jeszcze aktywowane. Kliknij link z maila uczelnianego albo poproś o nowy.";
    static final String INVALID_RESET_LINK = "Link do zmiany hasła jest nieprawidłowy lub wygasł. Poproś o nowy.";

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokens;
    private final EmailService emailService;
    private final AfterCommitMailer mailer;
    private final Clock clock;

    /** Skrót nieistniejącego hasła: logowanie na nieznany adres kosztuje tyle samo czasu co błędne hasło. */
    private volatile String dummyHash;

    /**
     * Zakłada konto. Odpowiedź dla klienta jest taka sama, czy adres był wolny, czy konto już istnieje: obcy nie może
     * tą drogą sprawdzać, kto ma konto. Dla istniejącego konta nic się nie dzieje (nie wysyłamy też maila, bo
     * takie maile można by wykorzystać do zalewania cudzej skrzynki). Hasło jest haszowane zawsze i na początku,
     * żeby czas odpowiedzi nie zdradzał, czy konto istnieje. Kto zgubił link aktywacyjny, ma "wyślij ponownie";
     * kto zapomniał hasła, ma reset.
     */
    @Transactional
    public void register(RegisterRequest request) {
        if (!Boolean.TRUE.equals(request.acceptedTerms())) {
            throw new IllegalArgumentException("Wymagana jest akceptacja regulaminu i polityki prywatności");
        }
        String passwordProblem = AuthRules.passwordProblem(request.password());
        if (passwordProblem != null) {
            throw new IllegalArgumentException(passwordProblem);
        }

        String passwordHash = passwordEncoder.encode(request.password());

        String privateEmail = AuthRules.normalizeEmail(request.privateEmail());
        String universityEmail = AuthRules.normalizeEmail(request.universityEmail());
        if (userRepository.existsByPrivateEmailIgnoreCase(privateEmail)
                || userRepository.existsByUniversityEmailIgnoreCase(universityEmail)) {
            log.info("Registration ignored: an account with one of these addresses already exists");
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        User user = new User();
        user.setPrivateEmail(privateEmail);
        user.setUniversityEmail(universityEmail);
        user.setPasswordHash(passwordHash);
        user.setTermsAcceptedAt(now);
        userRepository.save(user);

        EmailVerificationToken token = newVerificationToken(user, now);

        log.info("Verification link generated for university email {}: http://localhost:5173/verify?token={}",
                user.getUniversityEmail(), token.getToken());
        sendVerificationAfterCommit(user, token);
    }

    @Transactional
    public void verify(String rawToken) {
        EmailVerificationToken token = tokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new IllegalArgumentException("Nieprawidłowy link aktywacyjny"));

        User user = token.getUser();

        if (token.getUsedAt() != null) {
            if (user.isActive() && user.isUniversityEmailVerified()) {
                log.info("Token for user {} already used previously - returning success (idempotent)", user.getId());
                return;
            }
            throw new IllegalStateException("Link aktywacyjny został już wykorzystany");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (token.getExpiresAt().isBefore(now)) {
            throw new IllegalStateException("Link aktywacyjny wygasł. Poproś o nowy.");
        }

        user.setUniversityEmailVerified(true);
        user.setActive(true);
        userRepository.save(user);

        token.setUsedAt(now);
        tokenRepository.save(token);

        log.info("User {} successfully verified", user.getId());
    }

    /**
     * Ponownie wysyła link aktywacyjny. Zawsze kończy się tak samo, niezależnie od tego, czy konto istnieje,
     * żeby nie dało się sprawdzać cudzych adresów.
     */
    @Transactional
    public void resendVerification(String rawEmail) {
        Optional<User> found = userRepository.findFirstByPrivateEmailIgnoreCaseOrderByIdAsc(AuthRules.normalizeEmail(rawEmail));
        if (found.isEmpty() || found.get().isActive()) {
            return;
        }
        User user = found.get();
        LocalDateTime now = LocalDateTime.now(clock);

        Optional<EmailVerificationToken> last = tokenRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId());
        if (last.isPresent() && AuthRules.withinCooldown(last.get().getCreatedAt(), now, EMAIL_COOLDOWN)) {
            return;
        }

        EmailVerificationToken token = newVerificationToken(user, now);
        // Mail wychodzi po zatwierdzeniu i poza żądaniem: błąd poczty ani czas wysyłki nie zdradzają, że konto istnieje.
        sendVerificationAfterCommit(user, token);
    }

    /**
     * Wysyła link do zmiany hasła na zweryfikowany adres uczelniany konta (adres prywatny nie jest weryfikowany).
     * Zawsze kończy się tak samo, niezależnie od tego, czy konto istnieje.
     */
    @Transactional
    public void forgotPassword(String rawEmail) {
        Optional<User> found = userRepository.findFirstByPrivateEmailIgnoreCaseOrderByIdAsc(AuthRules.normalizeEmail(rawEmail));
        if (found.isEmpty() || !found.get().isActive()) {
            return;
        }
        User user = found.get();
        LocalDateTime now = LocalDateTime.now(clock);

        Optional<PasswordResetToken> last = resetTokenRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId());
        if (last.isPresent() && AuthRules.withinCooldown(last.get().getCreatedAt(), now, EMAIL_COOLDOWN)) {
            return;
        }

        String rawToken = TokenCodec.newToken();
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(TokenCodec.sha256Hex(rawToken));
        token.setExpiresAt(now.plus(RESET_TTL));
        token.setCreatedAt(now);
        resetTokenRepository.save(token);

        String universityEmail = user.getUniversityEmail();
        mailer.send("zmiana hasła, użytkownik " + user.getId(),
                () -> emailService.sendPasswordResetEmail(universityEmail, rawToken));
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        String passwordProblem = AuthRules.passwordProblem(newPassword);
        if (passwordProblem != null) {
            throw new IllegalArgumentException(passwordProblem);
        }

        PasswordResetToken token = resetTokenRepository.findByTokenHash(TokenCodec.sha256Hex(rawToken.trim()))
                .orElseThrow(() -> new IllegalArgumentException(INVALID_RESET_LINK));
        LocalDateTime now = LocalDateTime.now(clock);
        if (!AuthRules.isUsable(token.getUsedAt(), token.getExpiresAt(), now)) {
            throw new IllegalArgumentException(INVALID_RESET_LINK);
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        // Po resecie hasła wszystkie dotychczasowe sesje przestają działać (ktoś mógł mieć dostęp do konta).
        user.setTokensValidFrom(nowEpochSeconds());
        userRepository.save(user);

        closeOpenResetTokens(user, now);
        refreshTokens.revokeAll(user);
        log.info("Password changed for user {}", user.getId());
    }

    /**
     * Zmiana hasła zalogowanego użytkownika. Wymaga obecnego hasła (zły: {@link WrongPasswordException}).
     * Wszystkie dotychczasowe sesje przestają działać (tokeny dostępu przez {@code tokensValidFrom}, odświeżające przez
     * unieważnienie), a wywołujący dostaje świeżą parę tokenów dla bieżącej, więc nie zostaje wylogowany.
     * Otwarte linki do resetu hasła są unieważniane, a na adres uczelniany idzie powiadomienie o zmianie.
     */
    @Transactional
    public AuthResponse changePassword(String email, String currentPassword, String newPassword) {
        User user = userRepository.findByPrivateEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Konto użytkownika nie istnieje"));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            log.warn("Password change refused for user {}: wrong current password", user.getId());
            throw new WrongPasswordException("Obecne hasło jest nieprawidłowe");
        }
        String passwordProblem = AuthRules.passwordProblem(newPassword);
        if (passwordProblem != null) {
            throw new IllegalArgumentException(passwordProblem);
        }
        if (newPassword.equals(currentPassword)) {
            throw new IllegalArgumentException("Nowe hasło musi być inne niż obecne");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setTokensValidFrom(nowEpochSeconds());
        userRepository.save(user);
        closeOpenResetTokens(user, now);
        refreshTokens.revokeAll(user);

        String universityEmail = user.getUniversityEmail();
        mailer.send("zmiana hasła z konta, użytkownik " + user.getId(),
                () -> emailService.sendPasswordChangedEmail(universityEmail));
        log.info("Password changed from the account page for user {}", user.getId());

        return refreshTokens.startSession(user);
    }

    public AuthResponse login(LoginRequest request) {
        String email = AuthRules.normalizeEmail(request.privateEmail());
        User user = userRepository.findFirstByPrivateEmailIgnoreCaseOrderByIdAsc(email).orElse(null);

        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash());
            throw new IllegalArgumentException(INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Failed login attempt for user {}", user.getId());
            throw new IllegalArgumentException(INVALID_CREDENTIALS);
        }
        if (!user.isActive()) {
            throw new IllegalStateException(NOT_VERIFIED);
        }

        return refreshTokens.startSession(user);
    }

    /** Unieważnia ten link i wszystkie wcześniejsze, których nikt nie użył. */
    private void closeOpenResetTokens(User user, LocalDateTime now) {
        for (PasswordResetToken open : resetTokenRepository.findByUserIdAndUsedAtIsNull(user.getId())) {
            open.setUsedAt(now);
            resetTokenRepository.save(open);
        }
    }

    private void sendVerificationAfterCommit(User user, EmailVerificationToken token) {
        String universityEmail = user.getUniversityEmail();
        String rawToken = token.getToken();
        mailer.send("aktywacja konta " + user.getId(), () -> emailService.sendVerificationEmail(universityEmail, rawToken));
    }

    /** Aktualna chwila w sekundach od 1970, w tej samej mierze co claim iat w tokenie. */
    private long nowEpochSeconds() {
        return clock.instant().getEpochSecond();
    }

    private EmailVerificationToken newVerificationToken(User user, LocalDateTime now) {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setToken(UUID.randomUUID().toString());
        token.setExpiresAt(now.plus(VERIFICATION_TTL));
        token.setCreatedAt(now);
        return tokenRepository.save(token);
    }

    private String dummyHash() {
        String hash = dummyHash;
        if (hash == null) {
            hash = passwordEncoder.encode("gradus-nieistniejace-haslo");
            dummyHash = hash;
        }
        return hash;
    }
}
