package pl.tutlaw.backend.exam.dto;

import pl.tutlaw.backend.review.dto.TestQueueItemResponse;

import java.util.List;

/**
 * Pytania egzaminu (bez poprawnych odpowiedzi; te wracają dopiero po zakończeniu).
 *
 * @param requestedCount ile pytań użytkownik chciał; gdy w zakresie jest mniej dostępnych, {@code questions} jest krótsze
 */
public record ExamStartResponse(
        String scopeLabel,
        int requestedCount,
        List<TestQueueItemResponse> questions
) {
}
