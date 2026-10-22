package pl.gradusiuris.backend.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void concurrentUpdateOfTheSameCard_isAConflictWithAFriendlyMessage() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/review/test-answer");

        ResponseEntity<ErrorResponse> response = handler.handleConcurrentUpdate(
                new OptimisticLockingFailureException("stale"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().message()).contains("w innym oknie");
        // Szczegóły techniczne nie trafiają do klienta.
        assertThat(response.getBody().message()).doesNotContain("stale");
    }

    private static HttpServletRequest request() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/review/queue");
        when(request.getMethod()).thenReturn("GET");
        return request;
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    private static final class AnnotatedNotFound extends RuntimeException {
    }

    @Test
    void anUnexpectedErrorBecomesAnInternalServerErrorWithoutTechnicalDetails() throws Exception {
        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(
                new IllegalStateException("tabela users, kolumna password_hash, stos wywołań"), request());

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().message()).isEqualTo("Wystąpił nieoczekiwany błąd. Spróbuj ponownie za chwilę.");
        assertThat(response.getBody().message()).doesNotContain("password_hash");
        assertThat(response.getBody().path()).isEqualTo("/api/review/queue");
    }

    @Test
    void aCheckedExceptionIsAlsoTurnedIntoA500() throws Exception {
        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(new IOException("dysk"), request());

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().message()).doesNotContain("dysk");
    }

    @Test
    void accessDeniedIsPassedOnSoItStaysA403() {
        AccessDeniedException denied = new AccessDeniedException("Access Denied");

        assertThatThrownBy(() -> handler.handleUnexpected(denied, request())).isSameAs(denied);
    }

    @Test
    void missingAuthenticationIsPassedOnSoItStaysA401() {
        AuthenticationException unauthenticated = new AuthenticationException("Brak tokenu") { };

        assertThatThrownBy(() -> handler.handleUnexpected(unauthenticated, request())).isSameAs(unauthenticated);
    }

    @Test
    void springWebExceptionsWithTheirOwnStatusArePassedOn() {
        // 404 dla nieznanego adresu, 405, 415 itd. to ErrorResponse: mają własną, poprawną obsługę
        ResponseStatusException notFound = new ResponseStatusException(HttpStatus.NOT_FOUND, "brak");

        assertThatThrownBy(() -> handler.handleUnexpected(notFound, request())).isSameAs(notFound);
    }

    @Test
    void aBadParameterTypeIsPassedOnSoItStaysA400() {
        // np. ?categoryId=abc
        TypeMismatchException mismatch = new TypeMismatchException("abc", Long.class);

        assertThatThrownBy(() -> handler.handleUnexpected(mismatch, request())).isSameAs(mismatch);
    }

    @Test
    void exceptionsAnnotatedWithResponseStatusArePassedOn() {
        AnnotatedNotFound annotated = new AnnotatedNotFound();

        assertThatThrownBy(() -> handler.handleUnexpected(annotated, request())).isSameAs(annotated);
    }

    @Test
    void ordinaryClientErrorsAreExpected_soTheyAreNotReportedToSentry() {
        assertThat(GlobalExceptionHandler.isExpected(new IllegalArgumentException("zły parametr"))).isTrue();
        assertThat(GlobalExceptionHandler.isExpected(new IllegalStateException("konflikt"))).isTrue();
        assertThat(GlobalExceptionHandler.isExpected(new ConflictException("duplikat"))).isTrue();
        assertThat(GlobalExceptionHandler.isExpected(new UnauthorizedException("zły login"))).isTrue();
        assertThat(GlobalExceptionHandler.isExpected(new ResourceNotFoundException("brak"))).isTrue();
        assertThat(GlobalExceptionHandler.isExpected(new OptimisticLockingFailureException("stale"))).isTrue();
        assertThat(GlobalExceptionHandler.isExpected(new AccessDeniedException("403"))).isTrue();
        assertThat(GlobalExceptionHandler.isExpected(new ResponseStatusException(HttpStatus.NOT_FOUND))).isTrue();
        assertThat(GlobalExceptionHandler.isExpected(new AnnotatedNotFound())).isTrue();
    }

    @Test
    void genuinelyUnexpectedErrorsAreReportedToSentry() {
        assertThat(GlobalExceptionHandler.isExpected(new NullPointerException())).isFalse();
        assertThat(GlobalExceptionHandler.isExpected(new IOException("dysk"))).isFalse();
        assertThat(GlobalExceptionHandler.isExpected(new RuntimeException("awaria"))).isFalse();
        // Przyczyna nie ma znaczenia: ten handler odpowiada 500 na wyjątek zewnętrzny, więc zgłaszamy go jak 500.
        assertThat(GlobalExceptionHandler.isExpected(new RuntimeException("awaria", new IllegalArgumentException()))).isFalse();
    }
}
