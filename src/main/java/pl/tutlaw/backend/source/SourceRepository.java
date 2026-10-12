package pl.tutlaw.backend.source;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SourceRepository extends JpaRepository<Source, Long> {
    List<Source> findBySubjectId(Long subjectId);
}