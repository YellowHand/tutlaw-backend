package pl.tutlaw.backend.exam;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, Long> {

    /** Ile egzaminów próbnych użytkownik ukończył (do podsumowania nauki). */
    long countByUserId(Long userId);

    /** Kasuje wszystkie podejścia użytkownika (zerowanie postępów). Zwraca liczbę usuniętych. */
    @Modifying
    @Query("DELETE FROM ExamAttempt a WHERE a.user.id = :userId")
    int deleteAllByUser(@Param("userId") Long userId);

    /** Jedna strona podejść użytkownika, od najnowszego (z łączną liczbą podejść). */
    @Query(value = """
            SELECT a FROM ExamAttempt a
            WHERE a.user.id = :userId
            ORDER BY a.finishedAt DESC, a.id DESC
            """,
            countQuery = "SELECT COUNT(a) FROM ExamAttempt a WHERE a.user.id = :userId")
    Page<ExamAttempt> findPage(@Param("userId") Long userId, Pageable pageable);

    /** Podejście o tym numerze, ale tylko gdy należy do użytkownika (cudze wygląda jak nieistniejące). */
    Optional<ExamAttempt> findByIdAndUserId(Long id, Long userId);

    /** Wszystkie podejścia użytkownika (do eksportu danych), od najnowszego. */
    @Query("""
            SELECT a FROM ExamAttempt a
            WHERE a.user.id = :userId
            ORDER BY a.finishedAt DESC, a.id DESC
            """)
    List<ExamAttempt> findAllForUser(@Param("userId") Long userId);
}
