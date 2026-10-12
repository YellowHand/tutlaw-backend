package pl.tutlaw.backend.common.exception;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Jedno miejsce zamieniające wyjątki na odpowiedzi HTTP w jednym formacie ({@link ErrorResponse}).
 * Na końcu jest łapacz ogólnego {@code Exception}, który zamienia NIEOCZEKIWANE błędy w czytelne 500 bez
 * szczegółów technicznych. Przepuszcza dalej wszystko, co ma własną, poprawną obsługę (odmowa dostępu 403,
 * brak logowania 401, błędy żądania 400/404/405/415 ze Springa i wyjątki z @ResponseStatus), żeby ich nie
 * zamienić w 500 (zob. {@link #isHandledElsewhere}).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    /** Błędy walidacji pól (@Valid): zwracamy czytelną listę zamiast pustej odpowiedzi 400. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::describe)
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message.isEmpty() ? "Nieprawidłowe dane żądania" : message, request);
    }

    /** Zepsuty JSON albo wartość spoza dozwolonych (np. odpowiedź „D" zamiast A/B/C). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Nieprawidłowy format żądania", request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleConflict(IllegalStateException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(ConflictException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    /**
     * Naruszenie ograniczenia w bazie (unikalność, CHECK, klucz obcy), np. dwa równoległe zapisy tej samej rzeczy.
     * Szczegółów SQL nie oddajemy klientowi.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "Operacja narusza ograniczenia danych (np. taki rekord już istnieje)", request);
    }

    /**
     * Ten sam stan karty zmieniły równocześnie dwa żądania (np. dwa otwarte okna z nauką).
     * Klient może spróbować jeszcze raz: kolejny zapis zobaczy już aktualny stan.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleConcurrentUpdate(OptimisticLockingFailureException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT,
                "Ta karta została właśnie zmieniona w innym oknie. Spróbuj jeszcze raz.", request);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    /** Za dużo prób (np. logowania). Nagłówek Retry-After mówi przeglądarce i narzędziom, kiedy spróbować znowu. */
    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyRequests(TooManyRequestsException ex, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(), HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(), ex.getMessage(), request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(body);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    /**
     * Ostatnia deska ratunku dla błędów, których nikt nie przewidział (np. awaria bazy, błąd programisty).
     * Szczegóły trafiają tylko do logu, klient dostaje ogólny komunikat. Wyjątki obsługiwane gdzie indziej
     * rzucamy dalej: Spring (albo filtr bezpieczeństwa) odpowie na nie tak, jak zawsze.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) throws Exception {
        if (isHandledElsewhere(ex)) {
            throw ex;
        }
        log.error("Nieoczekiwany błąd w {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Wystąpił nieoczekiwany błąd. Spróbuj ponownie za chwilę.", request);
    }

    /**
     * Wyjątki, które mają własną obsługę i nie wolno ich zamieniać w 500:
     * odmowa dostępu (403) i brak uwierzytelnienia (401) ze Spring Security, wszystkie wyjątki Springa MVC
     * z własnym statusem (interfejs ErrorResponse: 404 dla nieznanego adresu, 405, 415, brak parametru...),
     * błędy typu parametru (np. ?categoryId=abc to 400), błędy serwletu i wyjątki oznaczone @ResponseStatus.
     */
    static boolean isHandledElsewhere(Exception ex) {
        return ex instanceof AccessDeniedException
                || ex instanceof AuthenticationException
                || ex instanceof org.springframework.web.ErrorResponse
                || ex instanceof TypeMismatchException
                || ex instanceof ServletException
                || ex.getClass().isAnnotationPresent(ResponseStatus.class);
    }

    private static String describe(FieldError error) {
        String reason = error.getDefaultMessage() == null ? "nieprawidłowa wartość" : error.getDefaultMessage();
        return FieldLabels.label(error.getField()) + ": " + reason;
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI()
        );
        return ResponseEntity.status(status).body(body);
    }
}
