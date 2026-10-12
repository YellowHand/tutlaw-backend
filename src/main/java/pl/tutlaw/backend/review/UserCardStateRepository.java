package pl.tutlaw.backend.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


@Repository
public interface UserCardStateRepository
        extends JpaRepository<UserCardState, UserCardState.UserCardStateId> {

    Optional<UserCardState> findByUserIdAndCardId(Long userId, Long cardId);

    List<UserCardState> findByUserIdAndCardIdIn(Long userId, List<Long> cardIds);

    @Query("""
            SELECT COUNT(s) FROM UserCardState s
            WHERE s.user.id = :userId
            AND s.reps = 1
            AND s.lastReviewAt >= :startOfDay
            AND s.lastReviewAt < :endOfDay
            """)
    int countNewCardsIntroducedToday(
            @Param("userId") Long userId,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay
    );

    @Query("""
            SELECT s FROM UserCardState s
            WHERE s.user.id = :userId
            AND s.card.source.subject.id = :subjectId
            AND s.dueDate BETWEEN :start AND :end
            """)
    List<UserCardState> findDueBetween(
            @Param("userId") Long userId,
            @Param("subjectId") Long subjectId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
            SELECT s FROM UserCardState s
            WHERE s.user.id = :userId
            AND s.dueDate BETWEEN :start AND :end
            """)
    List<UserCardState> findDueBetweenAnySubject(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}