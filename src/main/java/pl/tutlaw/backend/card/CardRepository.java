package pl.tutlaw.backend.card;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CardRepository extends JpaRepository<Card, Long> {

    List<Card> findByChapterId(Long chapterId);

    List<Card> findBySourceId(Long sourceId);

    List<Card> findBySource_Subject_Id(Long subjectId);

    @Query("""
            SELECT c FROM Card c
            WHERE c.source.subject.id = :subjectId
            AND (
                c.source.sourceType = pl.tutlaw.backend.source.SourceType.FLAT_POOL
                OR (c.chapter IS NOT NULL AND c.chapter.id IN (
                    SELECT p.chapter.id FROM ChapterProgress p
                    WHERE p.user.id = :userId AND p.read = true
                ))
            )
            """)
    List<Card> findReadableCardsForUserAndSubject(
            @Param("subjectId") Long subjectId,
            @Param("userId") Long userId
    );

    @Query("""
            SELECT c FROM Card c
            WHERE c.source.id = :sourceId
            AND (
                c.source.sourceType = pl.tutlaw.backend.source.SourceType.FLAT_POOL
                OR (c.chapter IS NOT NULL AND c.chapter.id IN (
                    SELECT p.chapter.id FROM ChapterProgress p
                    WHERE p.user.id = :userId AND p.read = true
                ))
            )
            """)
    List<Card> findReadableCardsForUserAndSource(
            @Param("sourceId") Long sourceId,
            @Param("userId") Long userId
    );

    /**
     * Wszystkie karty dostępne dla użytkownika we wszystkich przedmiotach
     * (pule płaskie oraz rozdziały oznaczone jako przeczytane).
     * Używane przez globalną kolejkę powtórek.
     */
    @Query("""
            SELECT c FROM Card c
            WHERE (
                c.source.sourceType = pl.tutlaw.backend.source.SourceType.FLAT_POOL
                OR (c.chapter IS NOT NULL AND c.chapter.id IN (
                    SELECT p.chapter.id FROM ChapterProgress p
                    WHERE p.user.id = :userId AND p.read = true
                ))
            )
            """)
    List<Card> findReadableCardsForUser(@Param("userId") Long userId);
}