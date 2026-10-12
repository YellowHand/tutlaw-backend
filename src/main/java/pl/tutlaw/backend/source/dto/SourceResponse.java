package pl.tutlaw.backend.source.dto;

import pl.tutlaw.backend.source.SourceType;

public record SourceResponse(
        Long id, Long subjectId, String name, String author, String edition, SourceType sourceType
) {
}