package pl.tutlaw.backend.exam.dto;

import java.time.LocalDateTime;

/**
 * Podsumowanie jednego podejścia (lista historii i nagłówek raportu).
 *
 * @param passPoints       próg zaliczenia w punktach (100 z 150 przy pełnym egzaminie); {@code null} przy podejściach sprzed
 *                         stałego formatu, które miały własny próg w procentach ({@code passThresholdPercent})
 * @param detailsAvailable czy da się otworzyć raport tego podejścia ({@code GET /api/exams/attempts/{id}}): odpowiedzi
 *                         zapisują się od poprawki 2 etapu 34, starsze podejścia mają tylko wynik
 */
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
        Integer passPoints,
        boolean passed,
        Integer timeLimitMinutes,
        int durationSeconds,
        boolean timedOut,
        boolean detailsAvailable
) {
}
