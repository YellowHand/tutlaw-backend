package pl.tutlaw.backend.card;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.tutlaw.backend.common.GroupCount;

import java.util.Collection;
import java.util.List;

public interface CardRepository extends JpaRepository<Card, Long> {

    List<Card> findByChapterId(Long chapterId);

    List<Card> findBySourceId(Long sourceId);

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

    /** Czy ta karta jest dostępna dla użytkownika (pula płaska albo przeczytany rozdział): 1 albo 0. */
    @Query("""
            SELECT COUNT(c) FROM Card c
            WHERE c.id = :cardId
            AND (
                c.source.sourceType = pl.tutlaw.backend.source.SourceType.FLAT_POOL
                OR (c.chapter IS NOT NULL AND c.chapter.id IN (
                    SELECT p.chapter.id FROM ChapterProgress p
                    WHERE p.user.id = :userId AND p.read = true
                ))
            )
            """)
    long countReadableById(@Param("cardId") Long cardId, @Param("userId") Long userId);

    /** Ile kart użytkownik może teraz ćwiczyć (we wszystkich przedmiotach). */
    @Query("""
            SELECT COUNT(c) FROM Card c
            WHERE (
                c.source.sourceType = pl.tutlaw.backend.source.SourceType.FLAT_POOL
                OR (c.chapter IS NOT NULL AND c.chapter.id IN (
                    SELECT p.chapter.id FROM ChapterProgress p
                    WHERE p.user.id = :userId AND p.read = true
                ))
            )
            """)
    long countReadableForUser(@Param("userId") Long userId);

    /** Ile kart użytkownik może teraz ćwiczyć w jednym przedmiocie. */
    @Query("""
            SELECT COUNT(c) FROM Card c
            WHERE c.source.subject.id = :subjectId
            AND (
                c.source.sourceType = pl.tutlaw.backend.source.SourceType.FLAT_POOL
                OR (c.chapter IS NOT NULL AND c.chapter.id IN (
                    SELECT p.chapter.id FROM ChapterProgress p
                    WHERE p.user.id = :userId AND p.read = true
                ))
            )
            """)
    long countReadableForUserAndSubject(@Param("subjectId") Long subjectId, @Param("userId") Long userId);

    /** Liczba kart w każdym rozdziale źródła (groupId = id rozdziału). */
    @Query("""
            SELECT c.chapter.id AS groupId, COUNT(c) AS total
            FROM Card c
            WHERE c.source.id = :sourceId AND c.chapter IS NOT NULL
            GROUP BY c.chapter.id
            """)
    List<GroupCount> countByChapterForSource(@Param("sourceId") Long sourceId);

    /** Liczba kart w każdym źródle przedmiotu (groupId = id źródła). */
    @Query("""
            SELECT c.source.id AS groupId, COUNT(c) AS total
            FROM Card c
            WHERE c.source.subject.id = :subjectId
            GROUP BY c.source.id
            """)
    List<GroupCount> countBySourceForSubject(@Param("subjectId") Long subjectId);

    /** Wszystkie pytania testowe (karty z poprawną odpowiedzią) w każdym źródle (groupId = id źródła). */
    @Query("""
            SELECT c.source.id AS groupId, COUNT(c) AS total
            FROM Card c
            WHERE c.correctOption IS NOT NULL
            GROUP BY c.source.id
            """)
    List<GroupCount> countTestQuestionsBySource();

    /** Pytania testowe dostępne dla użytkownika w każdym źródle (pula płaska albo przeczytany rozdział). */
    @Query("""
            SELECT c.source.id AS groupId, COUNT(c) AS total
            FROM Card c
            WHERE c.correctOption IS NOT NULL
            AND (
                c.source.sourceType = pl.tutlaw.backend.source.SourceType.FLAT_POOL
                OR (c.chapter IS NOT NULL AND c.chapter.id IN (
                    SELECT p.chapter.id FROM ChapterProgress p
                    WHERE p.user.id = :userId AND p.read = true
                ))
            )
            GROUP BY c.source.id
            """)
    List<GroupCount> countReadableTestQuestionsBySource(@Param("userId") Long userId);

    /**
     * Pytania testowe do ćwiczenia wskazanych kart: tylko te, do których użytkownik ma dostęp (pula płaska albo
     * przeczytany rozdział), mają format testowy i NIE są przez niego zawieszone. Źródło pobierane od razu,
     * bo kolejka pokazuje jego nazwę.
     */
    @Query("""
            SELECT c FROM Card c JOIN FETCH c.source
            WHERE c.id IN :ids
            AND c.correctOption IS NOT NULL
            AND NOT EXISTS (
                SELECT s.card.id FROM UserCardState s
                WHERE s.user.id = :userId AND s.card.id = c.id AND s.suspended = true
            )
            AND (
                c.source.sourceType = pl.tutlaw.backend.source.SourceType.FLAT_POOL
                OR (c.chapter IS NOT NULL AND c.chapter.id IN (
                    SELECT p.chapter.id FROM ChapterProgress p
                    WHERE p.user.id = :userId AND p.read = true
                ))
            )
            """)
    List<Card> findPracticeTestCards(@Param("ids") Collection<Long> ids, @Param("userId") Long userId);

    /** Dostępne karty użytkownika w jednej kategorii (rodzaju), do testu lub nauki z całego działu. */
    @Query("""
            SELECT c FROM Card c
            WHERE c.source.subject.category.id = :categoryId
            AND (
                c.source.sourceType = pl.tutlaw.backend.source.SourceType.FLAT_POOL
                OR (c.chapter IS NOT NULL AND c.chapter.id IN (
                    SELECT p.chapter.id FROM ChapterProgress p
                    WHERE p.user.id = :userId AND p.read = true
                ))
            )
            """)
    List<Card> findReadableCardsForUserAndCategory(
            @Param("categoryId") Long categoryId,
            @Param("userId") Long userId
    );
}
