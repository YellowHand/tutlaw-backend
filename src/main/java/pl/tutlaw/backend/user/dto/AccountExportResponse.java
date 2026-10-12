package pl.tutlaw.backend.user.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/** Wszystkie dane, które serwis przechowuje o użytkowniku (prawo dostępu i przenoszenia danych). Bez hasła i tokenów. */
public record AccountExportResponse(
        Instant exportedAt,
        Account account,
        List<ChapterProgressItem> chapterProgress,
        List<CardStateItem> cardStates,
        List<ReviewItem> reviewLogs,
        List<ReportItem> cardReports
) {

    public record Account(
            String privateEmail,
            String universityEmail,
            boolean universityEmailVerified,
            String role,
            LocalDateTime createdAt,
            LocalDateTime termsAcceptedAt
    ) {}

    public record ChapterProgressItem(Long chapterId, String chapterTitle, boolean read, LocalDateTime readAt) {}

    public record CardStateItem(
            Long cardId,
            String question,
            String state,
            int reps,
            int lapses,
            double stability,
            double difficulty,
            LocalDateTime dueDate,
            LocalDateTime lastReviewAt
    ) {}

    /** Zgłoszenia błędów w pytaniach wysłane przez użytkownika. */
    public record ReportItem(
            Long cardId,
            String question,
            String reason,
            String comment,
            String status,
            LocalDateTime createdAt
    ) {}

    public record ReviewItem(
            Long cardId,
            LocalDateTime reviewedAt,
            String mode,
            int rating,
            Boolean correct,
            boolean unsure
    ) {}
}
