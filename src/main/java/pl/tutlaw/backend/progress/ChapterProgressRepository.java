package pl.tutlaw.backend.progress;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChapterProgressRepository
        extends JpaRepository<ChapterProgress, ChapterProgress.ChapterProgressId> {

    Optional<ChapterProgress> findByUserIdAndChapterId(Long userId, Long chapterId);

    List<ChapterProgress> findByUserIdAndChapterIdIn(Long userId, List<Long> chapterIds);

    /** Postęp w rozdziałach z tytułami, do eksportu danych użytkownika. */
    @Query("""
            SELECT p.chapter.id AS chapterId, p.chapter.title AS chapterTitle, p.read AS readFlag, p.readAt AS readAt
            FROM ChapterProgress p
            WHERE p.user.id = :userId
            ORDER BY p.chapter.id
            """)
    List<ExportedProgress> findExportRows(@Param("userId") Long userId);

    interface ExportedProgress {
        Long getChapterId();

        String getChapterTitle();

        Boolean getReadFlag();

        LocalDateTime getReadAt();
    }
}
