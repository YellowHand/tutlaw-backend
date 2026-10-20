package pl.tutlaw.backend.flashcard;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface FlashcardAdditionRepository extends JpaRepository<FlashcardAddition, Long> {

    /** Numery pytań, które użytkownik sam dodał do fiszek. */
    @Query("SELECT a.questionId FROM FlashcardAddition a WHERE a.userId = :userId")
    List<Long> findQuestionIdsByUserId(@Param("userId") Long userId);

    boolean existsByUserIdAndQuestionId(Long userId, Long questionId);

    /**
     * Dodaje pytanie do fiszek użytkownika; gdy już tam jest (np. dwa kliknięcia naraz), nie robi nic i nie zgłasza
     * błędu. Natywne zapytanie z ON CONFLICT, bo wyjątek z unikalnego klucza zepsułby całą transakcję.
     */
    @Modifying
    @Query(value = """
            INSERT INTO flashcard_additions (user_id, question_id, added_at)
            VALUES (:userId, :questionId, :addedAt)
            ON CONFLICT (user_id, question_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("userId") Long userId, @Param("questionId") Long questionId, @Param("addedAt") LocalDateTime addedAt);

    /** Ręcznie dodane przez użytkownika pytania wraz z treścią, do eksportu jego danych. */
    @Query("""
            SELECT a.questionId AS questionId, q.body AS questionBody, a.addedAt AS addedAt
            FROM FlashcardAddition a, Question q
            WHERE a.userId = :userId AND q.id = a.questionId
            ORDER BY a.addedAt, a.id
            """)
    List<ExportedAddition> findExportRows(@Param("userId") Long userId);

    interface ExportedAddition {
        Long getQuestionId();

        String getQuestionBody();

        LocalDateTime getAddedAt();
    }

    /** Ile pytań użytkownik sam dodał do fiszek. */
    long countByUserId(Long userId);

    /** Kasuje wszystkie ręczne dodania do fiszek użytkownika (zerowanie postępów). Zwraca liczbę usuniętych. */
    @Modifying
    @Query("DELETE FROM FlashcardAddition a WHERE a.userId = :userId")
    int deleteAllByUser(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM FlashcardAddition a WHERE a.userId = :userId AND a.questionId = :questionId")
    int deleteByUserIdAndQuestionId(@Param("userId") Long userId, @Param("questionId") Long questionId);
}
