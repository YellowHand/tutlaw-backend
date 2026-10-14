package pl.tutlaw.backend.exam.dto;

import pl.tutlaw.backend.review.dto.TestQueueItemResponse;

import java.util.List;

/**
 * Pytania egzaminu (bez poprawnych odpowiedzi; te wracają dopiero po zakończeniu).
 *
 * @param requestedCount ile pytań ma pełny egzamin (zawsze 150); gdy w materiale jest mniej dostępnych pytań,
 *                       {@code questions} jest krótsze
 * @param passPoints     ile punktów trzeba mieć, żeby zdać ten egzamin: 100, a przy krótszym zestawie proporcjonalnie
 */
public record ExamStartResponse(
        String scopeLabel,
        int requestedCount,
        int passPoints,
        List<TestQueueItemResponse> questions
) {
}
