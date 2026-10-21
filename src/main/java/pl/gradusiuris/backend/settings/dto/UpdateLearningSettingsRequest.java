package pl.gradusiuris.backend.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Pełny zestaw ustawień (zastępuje poprzedni). {@code null} w polu liczbowym oznacza „wartość domyślna".
 * Daty egzaminu nie ustawia się tutaj: wybór ścieżki to {@code PUT /api/me/track}, a terminy ustawia administrator.
 * Kolejność odpowiedzi w testach jest zawsze losowa, więc nie jest ustawieniem.
 */
public record UpdateLearningSettingsRequest(
        @Min(value = 0, message = "nie może być ujemna") @Max(value = 200, message = "najwyżej 200") Integer newFlashcardsPerDay,
        @Min(value = 0, message = "nie może być ujemna") @Max(value = 500, message = "najwyżej 500") Integer newTestQuestionsPerDay,
        @Min(value = 10, message = "co najmniej 10") @Max(value = 1000, message = "najwyżej 1000") Integer maxReviewsPerDay,
        Double desiredRetention
) {
}
