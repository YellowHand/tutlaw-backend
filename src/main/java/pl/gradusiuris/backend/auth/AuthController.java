package pl.gradusiuris.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.gradusiuris.backend.auth.dto.*;
import pl.gradusiuris.backend.common.exception.UnauthorizedException;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final AuthRateLimiter rateLimiter;
    private final RefreshCookie refreshCookie;
    private final TrustedOrigins trustedOrigins;

    /** Odpowiedź jest zawsze taka sama (200), czy konto już istniało, czy nie. */
    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        rateLimiter.checkRegister(http.getRemoteAddr());
        try {
            authService.register(request);
        } catch (DataIntegrityViolationException e) {
            // Dwa równoległe żądania z tym samym adresem: unikalność w bazie przepuściła tylko jedno. Dla drugiego
            // (transakcja już wycofana) odpowiedź jest taka sama jak dla każdego istniejącego konta.
            log.info("Registration raced with another request for the same address; treated as existing account");
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/verify")
    public ResponseEntity<Void> verify(@RequestParam String token) {
        authService.verify(token);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/login")
    public ResponseEntity<AccessTokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        String ip = http.getRemoteAddr();
        rateLimiter.checkLogin(ip, request.privateEmail());
        AuthResponse response;
        try {
            response = authService.login(request);
        } catch (IllegalArgumentException e) {
            // Zły adres albo hasło. Konto niezweryfikowane (IllegalStateException) nie liczy się jako nieudana próba.
            rateLimiter.recordLoginFailure(ip, request.privateEmail());
            throw e;
        }
        rateLimiter.recordLoginSuccess(ip, request.privateEmail());
        return refreshCookie.sessionResponse(response);
    }

    /**
     * Wymienia token odświeżający (z ciasteczka HttpOnly) na nowy token dostępu i nowe ciasteczko. Brak ciasteczka,
     * nieznany, wygasły, unieważniony albo zużyty token to 401, a klient wraca wtedy do logowania. Endpoint jest publiczny,
     * bo token dostępu zwykle już wygasł. Odpowiada tylko na żądania z naszego frontendu (zob. {@link TrustedOrigins}).
     */
    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(
            @CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken,
            HttpServletRequest http) {
        trustedOrigins.requireTrusted(http.getHeader(HttpHeaders.ORIGIN));
        rateLimiter.checkRefresh(http.getRemoteAddr());
        AuthResponse pair = refreshTokenService.refresh(refreshToken)
                .orElseThrow(() -> new UnauthorizedException("Sesja wygasła. Zaloguj się ponownie."));
        return refreshCookie.sessionResponse(pair);
    }

    /** Wylogowanie: unieważnia sesję tokenu z ciasteczka i usuwa ciasteczko. Zawsze 204, także bez ciasteczka. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken,
            HttpServletRequest http) {
        trustedOrigins.requireTrusted(http.getHeader(HttpHeaders.ORIGIN));
        rateLimiter.checkRefresh(http.getRemoteAddr());
        refreshTokenService.revokeSession(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.clear())
                .build();
    }

    /** Odpowiedź jest zawsze taka sama (200), czy konto istnieje, czy nie. */
    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody EmailRequest request, HttpServletRequest http) {
        if (rateLimiter.allowEmailAction("resend", http.getRemoteAddr(), request.privateEmail())) {
            authService.resendVerification(request.privateEmail());
        }
        return ResponseEntity.ok().build();
    }

    /** Odpowiedź jest zawsze taka sama (200), czy konto istnieje, czy nie. */
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody EmailRequest request, HttpServletRequest http) {
        if (rateLimiter.allowEmailAction("forgot", http.getRemoteAddr(), request.privateEmail())) {
            authService.forgotPassword(request.privateEmail());
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest http) {
        rateLimiter.checkResetAttempt(http.getRemoteAddr());
        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok().build();
    }
}
