package pl.tutlaw.backend.common.exception;

/** Brak ważnego zalogowania (np. użytkownik z tokenu już nie istnieje). Zwracana jako HTTP 401. */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
