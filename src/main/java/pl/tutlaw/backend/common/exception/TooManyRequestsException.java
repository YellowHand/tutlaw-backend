package pl.tutlaw.backend.common.exception;

/** Przekroczony limit prób (np. logowania). Zwracana jako HTTP 429 z nagłówkiem Retry-After. */
public class TooManyRequestsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
