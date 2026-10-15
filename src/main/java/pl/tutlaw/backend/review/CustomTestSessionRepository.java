package pl.tutlaw.backend.review;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomTestSessionRepository extends JpaRepository<CustomTestSession, Long> {

    /** Ostatnie sesje użytkownika, od najnowszej. */
    @Query("""
            SELECT s FROM CustomTestSession s
            WHERE s.user.id = :userId
            ORDER BY s.startedAt DESC, s.id DESC
            """)
    List<CustomTestSession> findRecent(@Param("userId") Long userId, Pageable pageable);

    /** Wszystkie sesje użytkownika (do eksportu danych), od najnowszej. */
    @Query("""
            SELECT s FROM CustomTestSession s
            WHERE s.user.id = :userId
            ORDER BY s.startedAt DESC, s.id DESC
            """)
    List<CustomTestSession> findAllForUser(@Param("userId") Long userId);

    Optional<CustomTestSession> findByIdAndUserId(Long id, Long userId);

    /** Ile własnych testów użytkownik skończył (do statystyk). */
    long countByUserIdAndFinishedAtIsNotNull(Long userId);
}
