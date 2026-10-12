package pl.tutlaw.backend.chapter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChapterRepository extends JpaRepository<Chapter, Long> {
    List<Chapter> findBySourceIdOrderByOrderIndexAsc(Long sourceId);
}