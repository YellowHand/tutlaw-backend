package pl.tutlaw.backend.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.tutlaw.backend.auth.AuthRateLimiter;
import pl.tutlaw.backend.user.dto.AccountExportResponse;
import pl.tutlaw.backend.user.dto.DeleteAccountRequest;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final AuthRateLimiter rateLimiter;

    /** Kopia wszystkich danych użytkownika w formacie JSON. */
    @GetMapping("/export")
    public AccountExportResponse export(Authentication authentication) {
        return accountService.export(authentication);
    }

    /** Usuwa konto i wszystkie jego dane. Wymaga hasła, a jego zgadywanie podlega temu samemu limitowi co logowanie. */
    @DeleteMapping
    public ResponseEntity<Void> delete(
            @Valid @RequestBody DeleteAccountRequest request,
            Authentication authentication,
            HttpServletRequest http) {
        String ip = http.getRemoteAddr();
        String email = authentication.getName();
        rateLimiter.checkLogin(ip, email);
        try {
            accountService.deleteAccount(authentication, request.password());
        } catch (IllegalArgumentException e) {
            rateLimiter.recordLoginFailure(ip, email);
            throw e;
        }
        rateLimiter.recordLoginSuccess(ip, email);
        return ResponseEntity.noContent().build();
    }
}
