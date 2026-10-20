package pl.tutlaw.backend.review.dto;

import pl.tutlaw.backend.question.CorrectOption;
import pl.tutlaw.backend.question.dto.LawChangeInfo;
import pl.tutlaw.backend.source.SourceKind;

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
     * @param lawChange      zmiana w prawie dotykająca pytania ({@code null}, gdy jej nie ma)
     */
    public record Question(
            int position,
            Long questionId,
            String body,
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
            String actName,
            String sourceName,
            SourceKind sourceKind,
            Integer examYear,
            LawChangeInfo lawChange
    ) {
    }
}
