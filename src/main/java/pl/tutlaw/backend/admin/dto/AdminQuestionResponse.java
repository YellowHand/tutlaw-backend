package pl.tutlaw.backend.admin.dto;

import pl.tutlaw.backend.question.CorrectOption;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Pytanie w panelu administratora: pełna treść, położenie (akt, rozdział, źródło), widoczność, oznaczenia i otwarte zgłoszenia. */
public record AdminQuestionResponse(
        Long id,
        Long actId,
        String actName,
        String actShortName,
        Long sectionId,
        String sectionName,
        Long sourceId,
        String sourceName,
        String body,
        String optionA,
        String optionB,
        String optionC,
        CorrectOption correctOption,
        String explanation,
        String legalBasis,
        LocalDate validAsOf,
        LocalDate lawChangedAt,
        String lawChangeNote,
        boolean hidden,
        boolean flashcardHidden,
        LocalDateTime createdAt,
        long openReports,
        List<AdminQuestionMark> marks
) {
}
