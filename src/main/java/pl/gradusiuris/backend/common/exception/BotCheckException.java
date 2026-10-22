package pl.gradusiuris.backend.common.exception;

/**
 * Nieudane sprawdzenie „czy to człowiek” (Cloudflare Turnstile). Status 400: brak albo zły token (użytkownik ma odświeżyć
 * widget i spróbować jeszcze raz). Status 503: nie udało się skontaktować z Cloudflare (blokujemy, zamiast przepuszczać boty).
 */
public class BotCheckException extends RuntimeException {

    private final int status;

    public BotCheckException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
