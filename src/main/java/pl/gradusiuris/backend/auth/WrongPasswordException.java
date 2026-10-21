package pl.gradusiuris.backend.auth;

/**
 * Podane aktualne hasło jest nieprawidłowe. To nadal błąd 400 (jak każdy {@link IllegalArgumentException}), ale
 * osobny typ pozwala liczyć do limitu prób tylko prawdziwe pomyłki w haśle, a nie np. zbyt słabe nowe hasło.
 */
public class WrongPasswordException extends IllegalArgumentException {

    public WrongPasswordException(String message) {
        super(message);
    }
}
