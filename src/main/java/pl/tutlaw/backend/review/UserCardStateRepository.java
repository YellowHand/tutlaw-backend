package pl.tutlaw.backend.review;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pl.tutlaw.backend.card.QuestionOrigin;
import pl.tutlaw.backend.common.GroupCount;

import java.time.LocalDateTime;
import java.util.Collection;
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
            AND s.suspended = false
            AND s.card.hidden = false
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
            AND s.suspended = false
            AND s.card.hidden = false
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
            AND s.suspended = false
            AND s.card.hidden = false
            AND s.dueDate <= :end
            AND ((s.card.correctOption IS NULL AND :withFlashcards = true)
                 OR (s.card.correctOption IS NOT NULL AND :withTests = true))
            """)
    List<UserCardState> findDueUntil(
            @Param("userId") Long userId,
            @Param("subjectId") Long subjectId,
            @Param("end") LocalDateTime end,
            @Param("withFlashcards") boolean withFlashcards,
            @Param("withTests") boolean withTests
    );

    @Query("""
            SELECT s FROM UserCardState s
            WHERE s.user.id = :userId
            AND s.suspended = false
            AND s.card.hidden = false
            AND s.dueDate <= :end
            AND ((s.card.correctOption IS NULL AND :withFlashcards = true)
                 OR (s.card.correctOption IS NOT NULL AND :withTests = true))
            """)
    List<UserCardState> findDueUntilAnySubject(
            @Param("userId") Long userId,
            @Param("end") LocalDateTime end,
            @Param("withFlashcards") boolean withFlashcards,
            @Param("withTests") boolean withTests
    );

    /**
     * Id karty, termin, stabilność i data ostatniej powtórki – wystarcza do zbudowania kolejki
     * (w tym do policzenia szansy przypomnienia), bez ładowania całych encji
     * i bez zapytania na każdą kartę osobno. Zawiera także karty zawieszone (flaga {@code suspended}): sam fakt,
     * że karta ma stan, odróżnia ją od nowej, więc zawieszonych nie wolno tu pomijać, bo wróciłyby jako „nowe".
     */
    @Query("""
            SELECT s.card.id AS cardId, s.dueDate AS dueDate, s.stability AS stability, s.lastReviewAt AS lastReviewAt,
                   s.lapses AS lapses, s.suspended AS suspended
            FROM UserCardState s
            WHERE s.user.id = :userId
            """)
    List<CardDue> findCardDueByUserId(@Param("userId") Long userId);

    /**
     * Liczba kart, których termin już minął, w dostępnym dla użytkownika materiale.
     * Ten sam filtr dostępności co w kolejce, więc liczba zgadza się z tym, co dostanie w sesji.
     * {@code withFlashcards} i {@code withTests} wybierają rodzaj kart: fiszki (bez opcji) i/albo pytania testowe.
     * {@code testOrigins} (filtr pochodzenia, niepusty) zawęża tylko pytania testowe; fiszki go nie widzą.
     */
    @Query("""
            SELECT COUNT(s) FROM UserCardState s
            WHERE s.user.id = :userId
            AND s.suspended = false
            AND s.card.hidden = false
            AND s.dueDate <= :now
            AND (
                s.card.source.sourceType = pl.tutlaw.backend.source.SourceType.FLAT_POOL
                OR (s.card.chapter IS NOT NULL AND s.card.chapter.id IN (
                    SELECT p.chapter.id FROM ChapterProgress p
                    WHERE p.user.id = :userId AND p.read = true
                ))
            )
            AND ((s.card.correctOption IS NULL AND :withFlashcards = true)
                 OR (s.card.correctOption IS NOT NULL AND :withTests = true
                     AND s.card.origin IN :testOrigins))
            """)
    long countDue(
            @Param("userId") Long userId,
            @Param("now") LocalDateTime now,
            @Param("withFlashcards") boolean withFlashcards,
            @Param("withTests") boolean withTests,
            @Param("testOrigins") Collection<QuestionOrigin> testOrigins
    );

    /**
     * Ile kart rozpoczętych (co najmniej jedna powtórka) jest w materiale dostępnym dla użytkownika
     * i ma stabilność większą niż {@code stabilityAbove}. Dla „rozpoczętych" podaj -1, dla „utrwalonych" próg.
     */
    @Query("""
            SELECT COUNT(s) FROM UserCardState s
            WHERE s.user.id = :userId
            AND s.card.hidden = false
            AND s.reps > 0
            AND s.stability > :stabilityAbove
            AND (
                s.card.source.sourceType = pl.tutlaw.backend.source.SourceType.FLAT_POOL
                OR (s.card.chapter IS NOT NULL AND s.card.chapter.id IN (
                    SELECT p.chapter.id FROM ChapterProgress p
                    WHERE p.user.id = :userId AND p.read = true
                ))
            )
            AND ((s.card.correctOption IS NULL AND :withFlashcards = true)
                 OR (s.card.correctOption IS NOT NULL AND :withTests = true))
            """)
    long countStartedReadable(
            @Param("userId") Long userId,
            @Param("stabilityAbove") double stabilityAbove,
            @Param("withFlashcards") boolean withFlashcards,
            @Param("withTests") boolean withTests
    );

    /** To samo, ograniczone do jednego przedmiotu. */
    @Query("""
            SELECT COUNT(s) FROM UserCardState s
            WHERE s.user.id = :userId
            AND s.card.source.subject.id = :subjectId
            AND s.card.hidden = false
            AND s.reps > 0
            AND s.stability > :stabilityAbove
            AND (
                s.card.source.sourceType = pl.tutlaw.backend.source.SourceType.FLAT_POOL
                OR (s.card.chapter IS NOT NULL AND s.card.chapter.id IN (
                    SELECT p.chapter.id FROM ChapterProgress p
                    WHERE p.user.id = :userId AND p.read = true
                ))
            )
            AND ((s.card.correctOption IS NULL AND :withFlashcards = true)
                 OR (s.card.correctOption IS NOT NULL AND :withTests = true))
            """)
    long countStartedReadableInSubject(
            @Param("userId") Long userId,
            @Param("subjectId") Long subjectId,
            @Param("stabilityAbove") double stabilityAbove,
            @Param("withFlashcards") boolean withFlashcards,
            @Param("withTests") boolean withTests
    );

    /** Rozpoczęte karty w rozdziałach źródła ze stabilnością powyżej progu (groupId = id rozdziału). */
    @Query("""
            SELECT s.card.chapter.id AS groupId, COUNT(s) AS total
            FROM UserCardState s
            WHERE s.user.id = :userId
            AND s.card.source.id = :sourceId
            AND s.card.chapter IS NOT NULL
            AND s.card.hidden = false
            AND s.reps > 0
            AND s.stability > :stabilityAbove
            AND ((s.card.correctOption IS NULL AND :withFlashcards = true)
                 OR (s.card.correctOption IS NOT NULL AND :withTests = true))
            GROUP BY s.card.chapter.id
            """)
    List<GroupCount> countStartedByChapter(
            @Param("userId") Long userId,
            @Param("sourceId") Long sourceId,
            @Param("stabilityAbove") double stabilityAbove,
            @Param("withFlashcards") boolean withFlashcards,
            @Param("withTests") boolean withTests
    );

    /** Rozpoczęte karty w każdym źródle przedmiotu (groupId = id źródła). */
    @Query("""
            SELECT s.card.source.id AS groupId, COUNT(s) AS total
            FROM UserCardState s
            WHERE s.user.id = :userId
            AND s.card.source.subject.id = :subjectId
            AND s.card.hidden = false
            AND s.reps > 0
            AND ((s.card.correctOption IS NULL AND :withFlashcards = true)
                 OR (s.card.correctOption IS NOT NULL AND :withTests = true))
            GROUP BY s.card.source.id
            """)
    List<GroupCount> countStartedBySource(
            @Param("userId") Long userId,
            @Param("subjectId") Long subjectId,
            @Param("withFlashcards") boolean withFlashcards,
            @Param("withTests") boolean withTests
    );

    /** Skrót stanu rozpoczętych kart, wystarczający do wyliczenia najsłabszych bez ładowania encji. */
    @Query("""
            SELECT s.card.id AS cardId, s.stability AS stability, s.lapses AS lapses, s.lastReviewAt AS lastReviewAt
            FROM UserCardState s
            WHERE s.user.id = :userId AND s.reps > 0 AND s.lastReviewAt IS NOT NULL
            AND s.suspended = false AND s.card.hidden = false
            """)
    List<StartedCardSummary> findStartedSummaries(@Param("userId") Long userId);

    @Query("""
            SELECT s.card.id AS cardId, s.stability AS stability, s.lapses AS lapses, s.lastReviewAt AS lastReviewAt
            FROM UserCardState s
            WHERE s.user.id = :userId
            AND s.card.source.subject.id = :subjectId
            AND s.reps > 0 AND s.lastReviewAt IS NOT NULL
            AND s.suspended = false AND s.card.hidden = false
            """)
    List<StartedCardSummary> findStartedSummariesInSubject(
            @Param("userId") Long userId, @Param("subjectId") Long subjectId);

    /** Zawieszone pytania użytkownika, od ostatnio zawieszonych. */
    @Query("""
            SELECT s.card.id AS cardId, s.card.front AS front, s.card.source.name AS sourceName,
                   s.lapses AS lapses, s.suspendedAt AS suspendedAt
            FROM UserCardState s
            WHERE s.user.id = :userId AND s.suspended = true AND s.card.hidden = false
            ORDER BY s.suspendedAt DESC, s.card.id
            """)
    List<DifficultRow> findSuspendedRows(@Param("userId") Long userId, Pageable pageable);

    /** Numery wszystkich pytań zawieszonych przez użytkownika (egzamin próbny ich nie losuje). */
    @Query("SELECT s.card.id FROM UserCardState s WHERE s.user.id = :userId AND s.suspended = true")
    List<Long> findSuspendedCardIds(@Param("userId") Long userId);

    /**
     * Trudne pytania, które nadal są w nauce: nie zawieszone, w dostępnym dla użytkownika materiale
     * i z co najmniej {@code threshold} błędami. Najpierw te z największą liczbą błędów.
     */
    @Query("""
            SELECT s.card.id AS cardId, s.card.front AS front, s.card.source.name AS sourceName,
                   s.lapses AS lapses, s.suspendedAt AS suspendedAt
            FROM UserCardState s
            WHERE s.user.id = :userId
            AND s.suspended = false
            AND s.card.hidden = false
            AND s.lapses >= :threshold
            AND (
                s.card.source.sourceType = pl.tutlaw.backend.source.SourceType.FLAT_POOL
                OR (s.card.chapter IS NOT NULL AND s.card.chapter.id IN (
                    SELECT p.chapter.id FROM ChapterProgress p
                    WHERE p.user.id = :userId AND p.read = true
                ))
            )
            ORDER BY s.lapses DESC, s.card.id
            """)
    List<DifficultRow> findLeechRows(
            @Param("userId") Long userId, @Param("threshold") int threshold, Pageable pageable);

    /**
     * Kasuje stan nauki podanych kart użytkownika (np. fiszki, którą wycofał z Fiszek). Zwraca liczbę usuniętych
     * stanów. Historia odpowiedzi (review_logs) zostaje.
     */
    @Modifying
    @Query("DELETE FROM UserCardState s WHERE s.user.id = :userId AND s.card.id IN :cardIds")
    int deleteByUserIdAndCardIds(@Param("userId") Long userId, @Param("cardIds") java.util.Collection<Long> cardIds);

    /** Wszystkie stany kart użytkownika wraz z treścią pytania, do eksportu jego danych. */
    @Query("""
            SELECT s.card.id AS cardId, s.card.front AS cardFront, s.state AS state, s.reps AS reps,
                   s.lapses AS lapses, s.stability AS stability, s.difficulty AS difficulty,
                   s.dueDate AS dueDate, s.lastReviewAt AS lastReviewAt, s.suspended AS suspended
            FROM UserCardState s
            WHERE s.user.id = :userId
            ORDER BY s.card.id
            """)
    List<ExportedCardState> findExportRows(@Param("userId") Long userId);

    interface ExportedCardState {
        Long getCardId();

        String getCardFront();

        CardLearningState getState();

        Integer getReps();

        Integer getLapses();

        Double getStability();

        Double getDifficulty();

        LocalDateTime getDueDate();

        LocalDateTime getLastReviewAt();

        Boolean getSuspended();
    }

    interface DifficultRow {
        Long getCardId();

        String getFront();

        String getSourceName();

        Integer getLapses();

        LocalDateTime getSuspendedAt();
    }

    interface StartedCardSummary {
        Long getCardId();

        Double getStability();

        Integer getLapses();

        LocalDateTime getLastReviewAt();
    }

    interface CardDue {
        Long getCardId();

        LocalDateTime getDueDate();

        Double getStability();

        LocalDateTime getLastReviewAt();

        Integer getLapses();

        Boolean getSuspended();
    }

    /** Stan nauki pytań testowych w jednym pochodzeniu i roku egzaminu: utrwalone i z minionym terminem powtórki. */
    interface OriginYearStates {
        QuestionOrigin getOrigin();

        Integer getExamYear();

        Long getMature();

        Long getDue();
    }

    /**
     * Pytania testowe, które użytkownik już zaczął, w każdym pochodzeniu i roku egzaminu: ile jest utrwalonych
     * (stabilność powyżej progu) i ile ma już miniony termin powtórki (bez zawieszonych). Dostępność taka sama jak
     * w {@link #countDue} i {@link #countStartedReadable}.
     */
    @Query("""
            SELECT s.card.origin AS origin, s.card.examYear AS examYear,
                   SUM(CASE WHEN s.stability > :matureStability THEN 1 ELSE 0 END) AS mature,
                   SUM(CASE WHEN s.suspended = false AND s.dueDate <= :now THEN 1 ELSE 0 END) AS due
            FROM UserCardState s
            WHERE s.user.id = :userId
            AND s.card.correctOption IS NOT NULL
            AND s.card.hidden = false
            AND s.reps > 0
            AND (
                s.card.source.sourceType = pl.tutlaw.backend.source.SourceType.FLAT_POOL
                OR (s.card.chapter IS NOT NULL AND s.card.chapter.id IN (
                    SELECT p.chapter.id FROM ChapterProgress p
                    WHERE p.user.id = :userId AND p.read = true
                ))
            )
            GROUP BY s.card.origin, s.card.examYear
            """)
    List<OriginYearStates> countTestStatesByOriginYear(
            @Param("userId") Long userId,
            @Param("now") LocalDateTime now,
            @Param("matureStability") double matureStability
    );
}
