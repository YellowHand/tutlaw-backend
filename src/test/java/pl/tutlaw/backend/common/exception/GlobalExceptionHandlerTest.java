package pl.tutlaw.backend.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
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
}
