package pl.tutlaw.backend.statistics.dto;

public record ChapterMasteryResponse(
        Long chapterId,
        String chapterTitle,
        int totalCards,
        int cardsStarted,
        int matureCards,
        double masteryPercentage
) {
}