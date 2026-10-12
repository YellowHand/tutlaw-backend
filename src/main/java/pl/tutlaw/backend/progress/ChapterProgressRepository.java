package pl.tutlaw.backend.progress;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChapterProgressRepository
        extends JpaRepository<ChapterProgress, ChapterProgress.ChapterProgressId> {

    Optional<ChapterProgress> findByUserIdAndChapterId(Long userId, Long chapterId);

    List<ChapterProgress> findByUserIdAndChapterIdIn(Long userId, List<Long> chapterIds);
}