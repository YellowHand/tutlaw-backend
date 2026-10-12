package pl.tutlaw.backend.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pl.tutlaw.backend.common.GroupCount;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


@Repository
public interface UserCardStateRepository
        extends JpaRepository<UserCardState, UserCardState.UserCardStateId> {

    Optional<UserCardState> findByUserIdAndCardId(Long userId, Long cardId);

    List<UserCardState> findByUserIdAndCardIdIn(Long userId, List<Long> cardIds);

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

    /** Termin powtórki do wskazanej chwili włącznie, bez dolnej granicy (czyli także zaległe). */
    @Query("""
            SELECT s FROM UserCardState s
            WHERE s.user.id = :userId
            AND s.card.source.subject.id = :subjectId
            AND s.dueDate <= :end
            """)
    List<UserCardState> findDueUntil(
            @Param("userId") Long userId,
            @Param("subjectId") Long subjectId,
            @Param("end") LocalDateTime end
    );

    @Query("""
            SELECT s FROM UserCardState s
            WHERE s.user.id = :userId
            AND s.dueDate <= :end
            """)
    List<UserCardState> findDueUntilAnySubject(
            @Param("userId") Long userId,
            @Param("end") LocalDateTime end
    );

    /**
     * Same pary (id karty, termin) – wystarcza do zbudowania kolejki, bez ładowania
     * całych encji i bez zapytania na każdą kartę osobno.
     */
    @Query("""
            SELECT s.card.id AS cardId, s.dueDate AS dueDate
            FROM UserCardState s
            WHERE s.user.id = :userId
            """)
    List<CardDue> findCardDueByUserId(@Param("userId") Long userId);

    /**
     * Liczba kart, których termin już minął, w dostępnym dla użytkownika materiale.
     * Ten sam filtr dostępności co w kolejce, więc liczba zgadza się z tym, co dostanie w sesji.
     */
    @Query("""
            SELECT COUNT(s) FROM UserCardState s
            WHERE s.user.id = :userId
            AND s.dueDate <= :now
            AND (
                s.card.source.sourceType = pl.tutlaw.backend.source.SourceType.FLAT_POOL
                OR (s.card.chapter IS NOT NULL AND s.card.chapter.id IN (
                    SELECT p.chapter.id FROM ChapterProgress p
                    WHERE p.user.id = :userId AND p.read = true
                ))
            )
            """)
    long countDue(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /**
     * Ile kart rozpoczętych (co najmniej jedna powtórka) jest w materiale dostępnym dla użytkownika
     * i ma stabilność większą niż {@code stabilityAbove}. Dla „rozpoczętych" podaj -1, dla „utrwalonych" próg.
     */
    @Query("""
            SELECT COUNT(s) FROM UserCardState s
            WHERE s.user.id = :userId
            AND s.reps > 0
            AND s.stability > :stabilityAbove
            AND (
                s.card.source.sourceType = pl.tutlaw.backend.source.SourceType.FLAT_POOL
                OR (s.card.chapter IS NOT NULL AND s.card.chapter.id IN (
                    SELECT p.chapter.id FROM ChapterProgress p
                    WHERE p.user.id = :userId AND p.read = true
                ))
            )
            """)
    long countStartedReadable(@Param("userId") Long userId, @Param("stabilityAbove") double stabilityAbove);

    /** To samo, ograniczone do jednego przedmiotu. */
    @Query("""
            SELECT COUNT(s) FROM UserCardState s
            WHERE s.user.id = :userId
            AND s.card.source.subject.id = :subjectId
            AND s.reps > 0
            AND s.stability > :stabilityAbove
            AND (
                s.card.source.sourceType = pl.tutlaw.backend.source.SourceType.FLAT_POOL
                OR (s.card.chapter IS NOT NULL AND s.card.chapter.id IN (
                    SELECT p.chapter.id FROM ChapterProgress p
                    WHERE p.user.id = :userId AND p.read = true
                ))
            )
            """)
    long countStartedReadableInSubject(
            @Param("userId") Long userId,
            @Param("subjectId") Long subjectId,
            @Param("stabilityAbove") double stabilityAbove
    );

    /** Rozpoczęte karty w rozdziałach źródła ze stabilnością powyżej progu (groupId = id rozdziału). */
    @Query("""
            SELECT s.card.chapter.id AS groupId, COUNT(s) AS total
            FROM UserCardState s
            WHERE s.user.id = :userId
            AND s.card.source.id = :sourceId
            AND s.card.chapter IS NOT NULL
            AND s.reps > 0
            AND s.stability > :stabilityAbove
            GROUP BY s.card.chapter.id
            """)
    List<GroupCount> countStartedByChapter(
            @Param("userId") Long userId,
            @Param("sourceId") Long sourceId,
            @Param("stabilityAbove") double stabilityAbove
    );

    /** Rozpoczęte karty w każdym źródle przedmiotu (groupId = id źródła). */
    @Query("""
            SELECT s.card.source.id AS groupId, COUNT(s) AS total
            FROM UserCardState s
            WHERE s.user.id = :userId
            AND s.card.source.subject.id = :subjectId
            AND s.reps > 0
            GROUP BY s.card.source.id
            """)
    List<GroupCount> countStartedBySource(@Param("userId") Long userId, @Param("subjectId") Long subjectId);

    /** Skrót stanu rozpoczętych kart, wystarczający do wyliczenia najsłabszych bez ładowania encji. */
    @Query("""
            SELECT s.card.id AS cardId, s.stability AS stability, s.lapses AS lapses, s.lastReviewAt AS lastReviewAt
            FROM UserCardState s
            WHERE s.user.id = :userId AND s.reps > 0 AND s.lastReviewAt IS NOT NULL
            """)
    List<StartedCardSummary> findStartedSummaries(@Param("userId") Long userId);

    @Query("""
            SELECT s.card.id AS cardId, s.stability AS stability, s.lapses AS lapses, s.lastReviewAt AS lastReviewAt
            FROM UserCardState s
            WHERE s.user.id = :userId
            AND s.card.source.subject.id = :subjectId
            AND s.reps > 0 AND s.lastReviewAt IS NOT NULL
            """)
    List<StartedCardSummary> findStartedSummariesInSubject(
            @Param("userId") Long userId, @Param("subjectId") Long subjectId);

    interface StartedCardSummary {
        Long getCardId();

        Double getStability();

        Integer getLapses();

        LocalDateTime getLastReviewAt();
    }

    interface CardDue {
        Long getCardId();

        LocalDateTime getDueDate();
    }
}