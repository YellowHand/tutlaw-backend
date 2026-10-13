package pl.tutlaw.backend.security;

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
import pl.tutlaw.backend.auth.JwtService;
import pl.tutlaw.backend.user.UserRepository;

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
                if (isSessionStillValid(email, token)) {
                    String role = jwtService.extractRole(token);
                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                    var auth = new UsernamePasswordAuthenticationToken(email, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * Jedno lekkie zapytanie o konto (po indeksowanym adresie): konto usunięte albo token wystawiony przed zmianą
     * hasła to brak uwierzytelnienia, czyli 401, a nie sesja, która działa do wygaśnięcia tokenu.
     */
    private boolean isSessionStillValid(String email, String token) {
        Optional<UserRepository.SessionInfo> session = userRepository.findSessionInfoByPrivateEmail(email);
        return session.isPresent()
                && SessionRules.isSessionValid(
                        jwtService.extractIssuedAtEpochSeconds(token), session.get().getTokensValidFrom());
    }
}
