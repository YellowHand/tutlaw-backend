package pl.tutlaw.backend.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Pełny zestaw ustawień (zastępuje poprzedni). {@code null} w polu liczbowym oznacza „wartość domyślna",
 * a {@code null} w {@code shuffleOptions} to „tak". Daty egzaminu nie ustawia się tutaj: wybór aplikacji to
 * {@code PUT /api/me/exam}, a terminy ustawia administrator. Pole {@code examDate} ze starszych klientów jest ignorowane.
 */
public record UpdateLearningSettingsRequest(
        @Min(value = 0, message = "nie może być ujemna") @Max(value = 200, message = "najwyżej 200") Integer newCardsPerDay,
        @Min(value = 0, message = "nie może być ujemna") @Max(value = 500, message = "najwyżej 500") Integer newTestQuestionsPerDay,
        @Min(value = 10, message = "co najmniej 10") @Max(value = 1000, message = "najwyżej 1000") Integer maxReviewsPerDay,
        Double desiredRetention,
        Boolean shuffleOptions
) {
}
