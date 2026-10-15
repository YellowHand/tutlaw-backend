package pl.tutlaw.backend.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CustomTestSessionQuestionRepository extends JpaRepository<CustomTestSessionQuestion, Long> {

    /** Pytania podanych sesji (bez kart, do liczenia wyników), w kolejności sesji. */
    @Query("""
            SELECT q FROM CustomTestSessionQuestion q
            WHERE q.session.id IN :sessionIds
            ORDER BY q.session.id, q.position
            """)
    List<CustomTestSessionQuestion> findBySessionIds(@Param("sessionIds") Collection<Long> sessionIds);

    /** Pytania jednej sesji razem z kartami i źródłami, w kolejności sesji (widok szczegółów). */
    @Query("""
            SELECT q FROM CustomTestSessionQuestion q
            JOIN FETCH q.card c
            JOIN FETCH c.source
            WHERE q.session.id = :sessionId
            ORDER BY q.position
            """)
    List<CustomTestSessionQuestion> findDetailed(@Param("sessionId") Long sessionId);

    Optional<CustomTestSessionQuestion> findBySessionIdAndCardId(Long sessionId, Long cardId);

    long countBySessionIdAndAnsweredAtIsNull(Long sessionId);
}
