package pl.tutlaw.backend.card.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import pl.tutlaw.backend.card.CardType;
import pl.tutlaw.backend.card.CorrectOption;

import java.time.LocalDate;
import java.util.List;

public record CardRequest(
        Long chapterId,
        @NotNull Long sourceId,
        @NotNull CardType type,
        @NotBlank String front,
        /** Wymagane dla fiszki (karty bez opcji). Dla pytania testowego może być puste. */
        String back,
        Integer pageNumber,
        boolean verbatimQuote,
        List<Long> tagIds,
        String optionA,
        String optionB,
        String optionC,
        CorrectOption correctOption,
        String explanation,
        String legalBasis,
        LocalDate validAsOf,
        /** Tylko fiszka: pytanie testowe, po błędzie w którym fiszka pojawia się w Fiszkach. */
        Long linkedTestCardId
) {
}