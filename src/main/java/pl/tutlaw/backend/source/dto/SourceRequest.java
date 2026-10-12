package pl.tutlaw.backend.source.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import pl.tutlaw.backend.source.SourceType;

public record SourceRequest(
        @NotNull Long subjectId,
        @NotBlank String name,
        String author,
        String edition,
        @NotNull SourceType sourceType
) {
}