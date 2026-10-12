package pl.tutlaw.backend.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReviewLogRepository extends JpaRepository<ReviewLog, Long> {

    /**
     * Ile kart użytkownik zobaczył po raz pierwszy w danym przedziale czasu (w danym trybie).
     * Pierwsza powtórka to wpis bez {@code elapsedDays}.
     */
    @Query("""
            SELECT COUNT(r) FROM ReviewLog r
            WHERE r.user.id = :userId
            AND r.mode = :mode
            AND r.elapsedDays IS NULL
            AND r.reviewedAt >= :startOfDay
            AND r.reviewedAt < :endOfDay
            """)
    long countFirstReviewsBetween(
            @Param("userId") Long userId,
            @Param("mode") ReviewMode mode,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay
    );

    List<ReviewLog> findByUserIdAndReviewedAtAfter(Long userId, LocalDateTime since);

    @Query("""
            SELECT r FROM ReviewLog r
            WHERE r.user.id = :userId AND r.card.source.subject.id = :subjectId
            AND r.reviewedAt >= :since
            """)
    List<ReviewLog> findByUserIdAndSubjectIdSince(
            @Param("userId") Long userId,
            @Param("subjectId") Long subjectId,
            @Param("since") LocalDateTime since
    );
}