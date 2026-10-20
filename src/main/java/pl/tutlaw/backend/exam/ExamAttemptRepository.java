package pl.tutlaw.backend.exam;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, Long> {

    /** Ile egzaminów próbnych użytkownik ukończył (do podsumowania nauki). */
    long countByUserId(Long userId);

    /** Kasuje wszystkie podejścia użytkownika (zerowanie postępów). Zwraca liczbę usuniętych. */
    @Modifying
    @Query("DELETE FROM ExamAttempt a WHERE a.user.id = :userId")
    int deleteAllByUser(@Param("userId") Long userId);

    /** Ostatnie podejścia użytkownika, od najnowszego. */
    @Query("""
            SELECT a FROM ExamAttempt a
            WHERE a.user.id = :userId
            ORDER BY a.finishedAt DESC, a.id DESC
            """)
    List<ExamAttempt> findRecent(@Param("userId") Long userId, Pageable pageable);

    /** Wszystkie podejścia użytkownika (do eksportu danych), od najnowszego. */
    @Query("""
            SELECT a FROM ExamAttempt a
            WHERE a.user.id = :userId
            ORDER BY a.finishedAt DESC, a.id DESC
            """)
    List<ExamAttempt> findAllForUser(@Param("userId") Long userId);
}
