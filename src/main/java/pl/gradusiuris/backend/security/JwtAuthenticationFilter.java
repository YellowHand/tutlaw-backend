package pl.gradusiuris.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.gradusiuris.backend.auth.JwtService;
import pl.gradusiuris.backend.user.UserRepository;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            if (jwtService.isTokenValid(token)) {
                String email = jwtService.extractEmail(token);
                Optional<Long> userId = validSessionUserId(email, token);
                if (userId.isPresent()) {
                    String role = jwtService.extractRole(token);
                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                    var auth = new UsernamePasswordAuthenticationToken(email, null, authorities);
                    // Numer konta w szczegółach uwierzytelnienia (bez dodatkowego zapytania): czyta go GET /api/me i raportowanie
                    // błędów (Sentry), żeby wiedzieć, którego użytkownika dotyczy błąd, bez adresu e-mail.
                    auth.setDetails(userId.get());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * Jedno lekkie zapytanie o konto (po indeksowanym adresie): konto usunięte albo token wystawiony przed zmianą
     * hasła to brak uwierzytelnienia, czyli 401, a nie sesja, która działa do wygaśnięcia tokenu.
     * Zwraca numer konta, gdy sesja jest ważna, a pusty wynik, gdy nie.
     */
    private Optional<Long> validSessionUserId(String email, String token) {
        return userRepository.findSessionInfoByPrivateEmail(email)
                .filter(session -> SessionRules.isSessionValid(
                        jwtService.extractIssuedAtEpochSeconds(token), session.getTokensValidFrom()))
                .map(UserRepository.SessionInfo::getId);
    }
}
