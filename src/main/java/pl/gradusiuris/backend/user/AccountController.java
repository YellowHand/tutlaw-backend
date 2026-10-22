package pl.gradusiuris.backend.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.gradusiuris.backend.auth.AuthRateLimiter;
import pl.gradusiuris.backend.auth.AuthService;
import pl.gradusiuris.backend.auth.RefreshCookie;
import pl.gradusiuris.backend.auth.WrongPasswordException;
import pl.gradusiuris.backend.auth.dto.AccessTokenResponse;
import pl.gradusiuris.backend.auth.dto.AuthResponse;
import pl.gradusiuris.backend.user.dto.AccountExportResponse;
import pl.gradusiuris.backend.user.dto.ChangePasswordRequest;
import pl.gradusiuris.backend.user.dto.DeleteAccountRequest;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final AuthService authService;
    private final AuthRateLimiter rateLimiter;
    private final RefreshCookie refreshCookie;

    /** Kopia wszystkich danych użytkownika w formacie JSON. */
    @GetMapping("/export")
    public AccountExportResponse export(Authentication authentication) {
        return accountService.export(authentication);
    }

    /**
     * Zmiana hasła bez zmiany adresu e-mail. Wymaga obecnego hasła, a jego zgadywanie podlega temu samemu limitowi co
     * logowanie. Odpowiedź zawiera świeży token dostępu, a nowy token odświeżający trafia do ciasteczka: dotychczasowe sesje
     * (także ta) przestają działać.
     */
    @PutMapping("/password")
    public ResponseEntity<AccessTokenResponse> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication,
            HttpServletRequest http) {
        String ip = http.getRemoteAddr();
        String email = authentication.getName();
        rateLimiter.checkLogin(ip, email);
        AuthResponse response;
        try {
            response = authService.changePassword(email, request.currentPassword(), request.newPassword());
        } catch (WrongPasswordException e) {
            rateLimiter.recordLoginFailure(ip, email);
            throw e;
        }
        rateLimiter.recordLoginSuccess(ip, email);
        return refreshCookie.sessionResponse(response);
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
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.clear())
                .build();
    }
}
