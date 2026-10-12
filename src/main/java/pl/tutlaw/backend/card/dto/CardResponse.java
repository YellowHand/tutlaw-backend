package pl.tutlaw.backend.card.dto;

import pl.tutlaw.backend.card.CardType;
import pl.tutlaw.backend.card.CorrectOption;

import java.util.List;

public record CardResponse(
        Long id, Long chapterId, Long sourceId, CardType type,
        String front, String back, Integer pageNumber, boolean verbatimQuote,
        List<String> tagNames,
        String optionA, String optionB, String optionC, CorrectOption correctOption, String explanation
) {
}