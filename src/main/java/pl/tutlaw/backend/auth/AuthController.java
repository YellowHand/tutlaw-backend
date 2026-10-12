package pl.tutlaw.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.tutlaw.backend.auth.dto.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthRateLimiter rateLimiter;

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        rateLimiter.checkRegister(http.getRemoteAddr());
        authService.register(request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/verify")
    public ResponseEntity<Void> verify(@RequestParam String token) {
        authService.verify(token);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
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
        return ResponseEntity.ok(response);
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
