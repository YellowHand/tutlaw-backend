package pl.gradusiuris.backend.review.dto;

import pl.gradusiuris.backend.review.ReviewMode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Trudne i zawieszone pytania użytkownika (Konto). Zawieszenie dotyczy jednego trybu, więc to samo pytanie może
 * pojawić się dwa razy (w testach i w fiszkach).
 *
 * @param leechThreshold od ilu błędów pytanie uchodzi za trudne
 * @param suspended      zawieszone pytania, od ostatnio zawieszonych
 * @param leeches        trudne pytania, które nadal są w nauce (nie zawieszone), od najwięcej błędów
 */
public record DifficultQuestionsResponse(
        int leechThreshold,
        List<Item> suspended,
        List<Item> leeches
) {

    /** @param suspendedAt tylko dla zawieszonych */
    public record Item(
            Long questionId,
            ReviewMode mode,
            String body,
            String actName,
            int lapses,
            LocalDateTime suspendedAt
    ) {}
}
