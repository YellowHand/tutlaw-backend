package pl.tutlaw.backend.exam;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExamAttemptAnswerRepository extends JpaRepository<ExamAttemptAnswer, Long> {

    /**
     * Odpowiedzi jednego podejścia razem z pytaniem, jego aktem prawnym i źródłem (raport pokazuje ich nazwy), w kolejności
     * egzaminu.
     */
    @Query("""
            SELECT a FROM ExamAttemptAnswer a
            JOIN FETCH a.question q
            JOIN FETCH q.source
            JOIN FETCH q.legalAct
            WHERE a.attempt.id = :attemptId
            ORDER BY a.position
            """)
    List<ExamAttemptAnswer> findDetailed(@Param("attemptId") Long attemptId);
}
