package pl.gradusiuris.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Przed endpointami podatnymi na boty (rejestracja, logowanie, reset i ponowna wysyłka maila) sprawdza token Turnstile.
 * Działa w warstwie MVC, więc błąd wraca do klienta w zwykłym formacie ({@code GlobalExceptionHandler}). Zapytania wstępne
 * CORS (OPTIONS) i inne metody niż POST przepuszcza.
 */
public class TurnstileInterceptor implements HandlerInterceptor {

    private final TurnstileVerifier verifier;

    public TurnstileInterceptor(TurnstileVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("POST".equals(request.getMethod())) {
            verifier.verify(request.getHeader(TurnstileVerifier.HEADER), request.getRemoteAddr());
        }
        return true;
    }
}
