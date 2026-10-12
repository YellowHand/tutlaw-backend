package pl.tutlaw.backend.exam.dto;

import java.time.LocalDateTime;

/** Podsumowanie jednego podejścia (lista historii i nagłówek raportu). */
public record ExamAttemptResponse(
        Long id,
        LocalDateTime finishedAt,
        String scopeLabel,
        int questionCount,
        int answeredCount,
        int correctCount,
        /** Wynik w procentach, zaokrąglony do 0,1. */
        double scorePercent,
        int passThresholdPercent,
        boolean passed,
        Integer timeLimitMinutes,
        int durationSeconds,
        boolean timedOut
) {
}
