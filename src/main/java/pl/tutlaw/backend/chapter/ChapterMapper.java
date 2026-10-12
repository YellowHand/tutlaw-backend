package pl.tutlaw.backend.chapter;

import pl.tutlaw.backend.chapter.dto.ChapterResponse;


public class ChapterMapper {
    public static ChapterResponse toResponse(Chapter chapter, boolean isRead) {
        return new ChapterResponse(
                chapter.getId(), chapter.getSource().getId(),
                chapter.getParentChapter() != null ? chapter.getParentChapter().getId() : null,
                chapter.getTitle(), chapter.getOrderIndex(), isRead
        );
    }
}