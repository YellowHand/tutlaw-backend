package pl.tutlaw.backend.exam.dto;

import pl.tutlaw.backend.question.CorrectOption;
import pl.tutlaw.backend.question.dto.LawChangeInfo;
import pl.tutlaw.backend.source.SourceKind;

import java.time.LocalDate;
import java.util.List;

/**
 * Raport po egzaminie.
 *
 * @param excludedCount pytania z przesłanej listy, których nie dało się ocenić (usunięte albo już niedostępne);
 *                      nie wliczają się do wyniku. Zwykle 0.
 */
public record ExamResultResponse(
        ExamAttemptResponse attempt,
        int excludedCount,
        List<SourceBreakdown> bySource,
        List<QuestionResult> questions
) {

    /** Wynik w obrębie jednego źródła pytań (np. "Egzamin 2024"). */
    public record SourceBreakdown(String sourceName, int total, int correct) {
    }

    /**
     * Jedno pytanie z raportu, w kolejności egzaminu. Wyjaśnienie i podstawa prawna bywają puste.
     *
     * @param selectedOption null = bez odpowiedzi
     * @param lawChange      zmiana w prawie dotykająca pytania ({@code null}, gdy jej nie ma)
     */
    public record QuestionResult(
            Long questionId,
            String body,
            String optionA,
            String optionB,
            String optionC,
            CorrectOption selectedOption,
            CorrectOption correctOption,
            boolean correct,
            String explanation,
            String legalBasis,
            LocalDate validAsOf,
            String sourceName,
            SourceKind sourceKind,
            Integer examYear,
            String actName,
            String actShortName,
            LawChangeInfo lawChange
    ) {
    }
}
