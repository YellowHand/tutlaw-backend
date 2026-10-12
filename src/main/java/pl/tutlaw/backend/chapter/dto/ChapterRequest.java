package pl.tutlaw.backend.chapter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ChapterRequest(
        @NotNull Long sourceId,
        Long parentChapterId,
        @NotBlank String title,
        @NotNull Integer orderIndex
) {
}