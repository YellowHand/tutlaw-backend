package pl.tutlaw.backend.chapter.dto;

public record ChapterResponse(
        Long id, Long sourceId, Long parentChapterId, String title, Integer orderIndex, boolean isRead
) {
}