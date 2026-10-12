package pl.tutlaw.backend.common.exception;

/** Operacja koliduje z istniejącymi danymi (np. duplikat karty). Zwracana jako HTTP 409. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
