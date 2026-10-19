package pl.tutlaw.backend.user.dto;

import pl.tutlaw.backend.settings.Theme;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Wszystkie dane, które serwis przechowuje o użytkowniku (prawo dostępu i przenoszenia danych). Bez hasła i tokenów. */
public record AccountExportResponse(
        Instant exportedAt,
        Account account,
        List<QuestionStateItem> questionStates,
        List<ReviewItem> reviewLogs,
        List<ReportItem> questionReports,
        List<ExamEntry> examAttempts,
        List<FlashcardAdditionItem> flashcardAdditions,
        List<CustomTestItem> customTests,
        List<StudyTimeItem> studyTime,
        LearningSettings learningSettings
) {

    public record Account(
            String privateEmail,
            String universityEmail,
            boolean universityEmailVerified,
            String role,
            LocalDateTime createdAt,
            LocalDateTime termsAcceptedAt
    ) {}

    /** Stan nauki pytania w jednym z trybów (TEST albo FLASHCARD). */
    public record QuestionStateItem(
            Long questionId,
            String mode,
            String question,
            String state,
            int reps,
            int lapses,
            double stability,
            double difficulty,
            LocalDateTime dueDate,
            LocalDateTime lastReviewAt,
            boolean suspended
    ) {}

    /** Zgłoszenia błędów w pytaniach wysłane przez użytkownika. */
    public record ReportItem(
            Long questionId,
            String question,
            String reason,
            String comment,
            String status,
            LocalDateTime createdAt
    ) {}

    public record ReviewItem(
            Long questionId,
            LocalDateTime reviewedAt,
            String mode,
            int rating,
            Boolean correct,
            boolean unsure
    ) {}

    /** Podsumowania podejść do egzaminu próbnego (bez pojedynczych odpowiedzi). */
    public record ExamEntry(
            LocalDateTime finishedAt,
            String scope,
            int questionCount,
            int answeredCount,
            int correctCount,
            int passThresholdPercent,
            Integer passPoints,
            boolean passed,
            Integer timeLimitMinutes,
            int durationSeconds,
            boolean timedOut
    ) {}

    /** Zapisane własne testy ("Moje testy"): konfiguracja i wynik (odpowiedzi na pojedyncze pytania są w dzienniku odpowiedzi). */
    public record CustomTestItem(
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            String scope,
            String kind,
            int requestedCount,
            int questionCount,
            int answeredCount,
            int correctCount,
            int guessedCount,
            double timeSeconds
    ) {}

    /** Czas nauki w danym dniu, w sekundach (czas w aplikacji, osobno od czasu odpowiadania na pytania). */
    public record StudyTimeItem(LocalDate day, int seconds) {}

    /** Pytania, które użytkownik sam dodał do fiszek przyciskiem „Dodaj do fiszek". */
    public record FlashcardAdditionItem(Long questionId, String question, LocalDateTime addedAt) {}

    /**
     * Zapisane ustawienia nauki; pola {@code null} oznaczają wartość domyślną (dla {@code newTestQuestionsPerDay}
     * limit automatyczny, który idzie za planem do egzaminu).
     * Cały obiekt jest {@code null}, gdy użytkownik niczego nie ustawiał.
     */
    public record LearningSettings(
            Integer newFlashcardsPerDay,
            Integer newTestQuestionsPerDay,
            Integer maxReviewsPerDay,
            Double desiredRetention,
            /** Ścieżka, do której użytkownik się przygotowuje (data egzaminu bierze się z terminu aplikacji, nie jest zapisana na koncie). */
            String track,
            /** Wygląd aplikacji: DARK (ciemny) albo LIGHT (jasny). */
            Theme theme,
            boolean shuffleOptions,
            /** Filtr źródeł pytań w Testach: kody grup oddzielone przecinkami albo null (wszystkie). */
            String sourceFilter,
            /** Numery aktów prawnych, do których użytkownik zawęził pulę (puste: bez zawężenia). */
            List<Long> poolActIds,
            /** Stałe ukrycie podpowiedzi skrótów klawiszowych w testach, w fiszkach i ukrycie licznika czasu pytania. */
            boolean hintHiddenTest,
            boolean hintHiddenFlashcard,
            boolean questionTimerHidden
    ) {}
}
