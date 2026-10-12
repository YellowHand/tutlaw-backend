package pl.tutlaw.backend.subject;

import pl.tutlaw.backend.subject.dto.SubjectResponse;

public class SubjectMapper {
    public static SubjectResponse toResponse(Subject subject) {
        return new SubjectResponse(subject.getId(), subject.getCategory().getId(), subject.getName(), subject.getSlug());
    }
}