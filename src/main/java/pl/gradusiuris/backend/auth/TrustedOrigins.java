package pl.gradusiuris.backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import pl.gradusiuris.backend.common.config.CorsConfig;

import java.util.List;

/**
 * Dodatkowa ochrona przed fałszowaniem żądań z cudzych stron (CSRF) dla endpointów, które działają na ciasteczku
 * (odświeżenie sesji i wylogowanie). Podstawą jest {@code SameSite=Strict}; tu odrzucamy jeszcze żądania, które przeglądarka
 * oznaczyła jako pochodzące z innej strony niż nasz frontend. Brak nagłówka Origin (narzędzia spoza przeglądarki) przepuszczamy:
 * przeglądarka wysyła go zawsze przy żądaniach POST z innej witryny.
 */
@Component
public class TrustedOrigins {

    private final List<String> allowed;

    public TrustedOrigins(@Value("${app.cors.allowed-origin}") String allowedOrigins) {
        this.allowed = CorsConfig.parseOrigins(allowedOrigins);
    }

    /** Rzuje 403, gdy żądanie przyszło z obcej strony. */
    public void requireTrusted(String origin) {
        if (origin == null || origin.isBlank()) {
            return;
        }
        if (!allowed.contains(origin.trim())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Niedozwolone źródło żądania");
        }
    }
}
