package pl.tutlaw.backend.review.dto;

import pl.tutlaw.backend.card.CorrectOption;
import pl.tutlaw.backend.card.QuestionOrigin;

import java.time.LocalDate;
import java.util.List;

/** Szczegóły zapisanego własnego testu: podsumowanie i wszystkie pytania z odpowiedziami użytkownika. */
public record CustomTestSessionDetailResponse(
        CustomTestSessionSummaryResponse summary,
        List<Question> questions
) {

    /**
     * @param selectedOption odpowiedź użytkownika pod oryginalnym kluczem z bazy; {@code null}, gdy nie odpowiedział
     * @param correct        {@code null}, gdy nie odpowiedział
     * @param timeSeconds    czas odpowiedzi; {@code null}, gdy nie odpowiedział albo brak pomiaru
     */
    public record Question(
            int position,
            Long cardId,
            String front,
            String optionA,
            String optionB,
            String optionC,
            CorrectOption correctOption,
            CorrectOption selectedOption,
            Boolean correct,
            boolean guessed,
            Double timeSeconds,
            String explanation,
            String legalBasis,
            LocalDate validAsOf,
            String sourceName,
            QuestionOrigin origin,
            Integer examYear
    ) {
    }
}
