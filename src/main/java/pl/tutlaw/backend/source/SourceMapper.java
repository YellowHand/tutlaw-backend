package pl.tutlaw.backend.source;

import pl.tutlaw.backend.source.dto.SourceResponse;

public class SourceMapper {
    public static SourceResponse toResponse(Source source) {
        return new SourceResponse(
                source.getId(),
                source.getSubject().getId(),
                source.getName(),
                source.getAuthor(),
                source.getEdition(),
                source.getSourceType()
        );
    }
}