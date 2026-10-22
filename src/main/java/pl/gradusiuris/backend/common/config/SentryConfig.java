package pl.gradusiuris.backend.common.config;

import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.Request;
import io.sentry.protocol.User;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import pl.gradusiuris.backend.common.exception.GlobalExceptionHandler;

/**
 * Filtr zgłoszeń wysyłanych do Sentry. Sam Sentry jest włączany wyłącznie zmienną SENTRY_DSN (bez niej cała biblioteka jest
 * nieaktywna), a ten filtr działa tylko wtedy, gdy Sentry jest włączony.
 *
 * Dwie zasady:
 *  1. Do Sentry idą błędy, które wymagają naprawy w kodzie: te, które kończą się odpowiedzią 500, i wpisy ERROR z logu.
 *     Błędy „zwykłe” (zły login, brak zasobu, walidacja, konflikt, limit prób) już mają swoją obsługę
 *     ({@link GlobalExceptionHandler#isExpected}) i zalałyby skrzynkę szumem.
 *  2. Żadnych danych osobowych ani tokenów: z adresu żądania usuwamy zapytanie (?token=... w linku potwierdzającym konto),
 *     a treść żądania nie jest dołączana. Jedyne, co mówi o użytkowniku, to numer konta (np. „5”), bez adresu e-mail i IP.
 */
@Configuration
public class SentryConfig {

    @Bean
    SentryOptions.BeforeSendCallback sentryBeforeSend() {
        return (event, hint) -> {
            Throwable throwable = event.getThrowable();
            if (throwable != null && GlobalExceptionHandler.isExpected(throwable)) {
                return null;
            }
            scrubRequest(event);
            attachUserId(event);
            return event;
        };
    }

    /**
     * Dołącza numer konta zalogowanego użytkownika. Zgłoszenie powstaje w wątku obsługującym żądanie, więc kontekst
     * bezpieczeństwa jest jeszcze dostępny. Numer ustawia JwtAuthenticationFilter w szczegółach uwierzytelnienia.
     */
    static void attachUserId(SentryEvent event) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getDetails() instanceof Long userId) {
            User user = new User();
            user.setId(String.valueOf(userId));
            event.setUser(user);
        }
    }

    static void scrubRequest(SentryEvent event) {
        Request request = event.getRequest();
        if (request == null) {
            return;
        }
        String url = request.getUrl();
        if (url != null) {
            request.setUrl(withoutQuery(url));
        }
        request.setQueryString(null);
        request.setData(null);
        request.setCookies(null);
    }

    static String withoutQuery(String url) {
        int cut = url.length();
        int query = url.indexOf('?');
        int fragment = url.indexOf('#');
        if (query >= 0) {
            cut = Math.min(cut, query);
        }
        if (fragment >= 0) {
            cut = Math.min(cut, fragment);
        }
        return url.substring(0, cut);
    }
}
