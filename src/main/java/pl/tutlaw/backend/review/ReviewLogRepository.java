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

    /** Same chwile odpowiedzi od podanego momentu (do mapy aktywności), bez ładowania całych wpisów. */
    @Query("""
            SELECT r.reviewedAt FROM ReviewLog r
            WHERE r.user.id = :userId AND r.reviewedAt >= :since
            """)
    List<LocalDateTime> findReviewTimesSince(
            @Param("userId") Long userId,
            @Param("since") LocalDateTime since
    );

    /**
     * Liczba prawdziwych powtórek (odpowiedzi na kartę widzianą wcześniej, po co najmniej {@code minElapsedDays}
     * dniach) z oceną co najmniej {@code minRating}. Podstawa wskaźnika zapamiętywania:
     * z minRating = 1 to wszystkie takie powtórki, z minRating = 2 tylko udane.
     * Pierwsze zobaczenie karty i powtórki tego samego dnia nie mówią nic o zapamiętywaniu, więc nie wchodzą.
     */
    @Query("""
            SELECT COUNT(r) FROM ReviewLog r
            WHERE r.user.id = :userId
            AND r.reviewedAt >= :since
            AND r.elapsedDays >= :minElapsedDays
            AND r.rating >= :minRating
            """)
    long countRecalls(
            @Param("userId") Long userId,
            @Param("since") LocalDateTime since,
            @Param("minElapsedDays") double minElapsedDays,
            @Param("minRating") int minRating
    );

    @Query("""
            SELECT COUNT(r) FROM ReviewLog r
            WHERE r.user.id = :userId
            AND r.card.source.subject.id = :subjectId
            AND r.reviewedAt >= :since
            AND r.elapsedDays >= :minElapsedDays
            AND r.rating >= :minRating
            """)
    long countRecallsInSubject(
            @Param("userId") Long userId,
            @Param("subjectId") Long subjectId,
            @Param("since") LocalDateTime since,
            @Param("minElapsedDays") double minElapsedDays,
            @Param("minRating") int minRating
    );
}
