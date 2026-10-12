package pl.tutlaw.backend.review.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Trudne i zawieszone pytania użytkownika (Konto).
 *
 * @param leechThreshold od ilu błędów pytanie uchodzi za trudne
 * @param suspended      zawieszone pytania, od ostatnio zawieszonych
 * @param leeches        trudne pytania, które nadal są w nauce (nie zawieszone), od najwięcej błędów
 */
public record DifficultCardsResponse(
        int leechThreshold,
        List<Item> suspended,
        List<Item> leeches
) {

    /** @param suspendedAt tylko dla zawieszonych */
    public record Item(
            Long cardId,
            String front,
            String sourceName,
            int lapses,
            LocalDateTime suspendedAt
    ) {}
}
