package pl.tutlaw.backend.statistics.dto;

import pl.tutlaw.backend.review.ReviewMode;

import java.time.LocalDateTime;

/** Pytanie, które użytkownik najłatwiej zapomni, w danym trybie nauki (test albo fiszka). */
public record WeakQuestionResponse(
        Long questionId,
        String body,
        ReviewMode mode,
        int lapses,
        double currentRetrievability,
        LocalDateTime lastReviewAt
) {
}
