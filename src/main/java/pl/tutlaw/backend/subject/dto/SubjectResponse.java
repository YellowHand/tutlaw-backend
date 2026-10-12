package pl.tutlaw.backend.subject.dto;

public record SubjectResponse(Long id, Long categoryId, String name, String slug) {
}