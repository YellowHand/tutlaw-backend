package pl.gradusiuris.backend.review;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pl.gradusiuris.backend.common.GroupCount;
import pl.gradusiuris.backend.pool.PoolJpql;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Zapytania o stan nauki. Wszystkie dotyczą jednego trybu ({@code mode}); pytania liczone są w puli użytkownika
 * ({@link PoolJpql#STATE}): {@code actIds} i {@code sourceIds} pochodzą z {@code Pool}. Dla fiszek wołający podaje pulę
 * fiszkową ({@code Pool#forFlashcards}), czyli bez filtra źródeł.
 */
@Repository
public interface UserQuestionStateRepository extends JpaRepository<UserQuestionState, Long> {

    Optional<UserQuestionState> findByUserIdAndQuestionIdAndMode(Long userId, Long questionId, ReviewMode mode);

    /** Ile pytań użytkownik ma w nauce w trybie (stany, także zawieszone). */
    long countByUserIdAndMode(Long userId, ReviewMode mode);

    /** Kasuje cały stan nauki użytkownika w obu trybach (zerowanie postępów). Zwraca liczbę usuniętych stanów. */
    @Modifying
    @Query("DELETE FROM UserQuestionState s WHERE s.user.id = :userId")
    int deleteAllByUser(@Param("userId") Long userId);

    /**
     * Numer pytania, termin, stabilność i data ostatniej powtórki – wystarcza do zbudowania kolejki (w tym do
     * policzenia szansy przypomnienia), bez ładowania całych encji i bez zapytania na każde pytanie osobno. Zawiera
     * także stany zawieszone (flaga {@code suspended}): sam fakt, że pytanie ma stan, odróżnia je od nowego, więc
     * zawieszonych nie wolno tu pomijać, bo wróciłyby jako „nowe".
     */
    @Query("""
            SELECT s.question.id AS questionId, s.dueDate AS dueDate, s.stability AS stability,
                   s.lastReviewAt AS lastReviewAt, s.lapses AS lapses, s.suspended AS suspended
            FROM UserQuestionState s
            WHERE s.user.id = :userId AND s.mode = :mode
            """)
    List<QuestionDue> findScheduleByUserIdAndMode(@Param("userId") Long userId, @Param("mode") ReviewMode mode);

    /** Liczba pytań w trybie z minionym terminem powtórki (do {@code now} włącznie), bez zawieszonych. */
    @Query("""
            SELECT COUNT(s) FROM UserQuestionState s
            WHERE s.user.id = :userId AND s.mode = :mode
            AND s.suspended = false
            AND s.dueDate <= :now
            AND\s""" + PoolJpql.STATE)
    long countDue(
            @Param("userId") Long userId,
            @Param("mode") ReviewMode mode,
            @Param("now") LocalDateTime now,
            @Param("actIds") Collection<Long> actIds,
            @Param("sourceIds") Collection<Long> sourceIds
    );

    /**
     * Ile pytań rozpoczętych (co najmniej jedna powtórka) jest w puli, w podziale na akty prawne (groupId = numer
     * aktu), ze stabilnością większą niż {@code stabilityAbove}. Dla „rozpoczętych" podaj -1, dla „utrwalonych" próg.
     */
    @Query("""
            SELECT s.question.legalAct.id AS groupId, COUNT(s) AS total
            FROM UserQuestionState s
            WHERE s.user.id = :userId AND s.mode = :mode
            AND s.reps > 0
            AND s.stability > :stabilityAbove
            AND\s""" + PoolJpql.STATE + """
            
            GROUP BY s.question.legalAct.id
            """)
    List<GroupCount> countStartedByAct(
            @Param("userId") Long userId,
            @Param("mode") ReviewMode mode,
            @Param("stabilityAbove") double stabilityAbove,
            @Param("actIds") Collection<Long> actIds,
            @Param("sourceIds") Collection<Long> sourceIds
    );

    /** Skrót stanu rozpoczętych pytań, wystarczający do wyliczenia najsłabszych bez ładowania encji. */
    @Query("""
            SELECT s.question.id AS questionId, s.question.legalAct.id AS legalActId, s.stability AS stability,
                   s.lapses AS lapses, s.lastReviewAt AS lastReviewAt, s.mode AS mode
            FROM UserQuestionState s
            WHERE s.user.id = :userId AND s.mode = :mode
            AND s.reps > 0 AND s.lastReviewAt IS NOT NULL
            AND s.suspended = false
            AND\s""" + PoolJpql.STATE)
    List<StartedSummary> findStartedSummaries(
            @Param("userId") Long userId,
            @Param("mode") ReviewMode mode,
            @Param("actIds") Collection<Long> actIds,
            @Param("sourceIds") Collection<Long> sourceIds
    );

    /** Terminy powtórek do końca podanego dnia, razem z aktem (zapowiedź powtórek na najbliższe dni). */
    @Query("""
            SELECT s.question.legalAct.id AS legalActId, s.dueDate AS dueDate
            FROM UserQuestionState s
            WHERE s.user.id = :userId AND s.mode = :mode
            AND s.suspended = false
            AND s.dueDate <= :end
            AND\s""" + PoolJpql.STATE)
    List<DueRow> findDueUntil(
            @Param("userId") Long userId,
            @Param("mode") ReviewMode mode,
            @Param("end") LocalDateTime end,
            @Param("actIds") Collection<Long> actIds,
            @Param("sourceIds") Collection<Long> sourceIds
    );

    /** Zawieszone pytania użytkownika (w obu trybach), od ostatnio zawieszonych. */
    @Query("""
            SELECT s.question.id AS questionId, s.mode AS mode, s.question.body AS body,
                   s.question.legalAct.name AS actName, s.lapses AS lapses, s.suspendedAt AS suspendedAt
            FROM UserQuestionState s
            WHERE s.user.id = :userId AND s.suspended = true AND s.question.hidden = false
            ORDER BY s.suspendedAt DESC, s.question.id
            """)
    List<DifficultRow> findSuspendedRows(@Param("userId") Long userId, Pageable pageable);

    /** Numery pytań zawieszonych przez użytkownika w trybie (egzamin próbny nie losuje zawieszonych w testach). */
    @Query("SELECT s.question.id FROM UserQuestionState s WHERE s.user.id = :userId AND s.mode = :mode AND s.suspended = true")
    List<Long> findSuspendedQuestionIds(@Param("userId") Long userId, @Param("mode") ReviewMode mode);

    /**
     * Trudne pytania, które nadal są w nauce: nie zawieszone, w puli użytkownika i z co najmniej {@code threshold}
     * błędami. Najpierw te z największą liczbą błędów.
     */
    @Query("""
            SELECT s.question.id AS questionId, s.mode AS mode, s.question.body AS body,
                   s.question.legalAct.name AS actName, s.lapses AS lapses, s.suspendedAt AS suspendedAt
            FROM UserQuestionState s
            WHERE s.user.id = :userId
            AND s.suspended = false
            AND s.lapses >= :threshold
            AND s.question.hidden = false
            AND s.question.legalAct.id IN :actIds
            ORDER BY s.lapses DESC, s.question.id
            """)
    List<DifficultRow> findLeechRows(
            @Param("userId") Long userId,
            @Param("threshold") int threshold,
            @Param("actIds") Collection<Long> actIds,
            Pageable pageable);

    /**
     * Kasuje stan nauki podanych pytań użytkownika w trybie (np. fiszki, którą wycofał z Fiszek). Zwraca liczbę
     * usuniętych stanów. Historia odpowiedzi (review_logs) zostaje.
     */
    @Modifying
    @Query("DELETE FROM UserQuestionState s WHERE s.user.id = :userId AND s.mode = :mode AND s.question.id IN :questionIds")
    int deleteByUserIdAndModeAndQuestionIds(
            @Param("userId") Long userId, @Param("mode") ReviewMode mode,
            @Param("questionIds") Collection<Long> questionIds);

    /**
     * Zmiana w prawie: pytania, których zmiana weszła w życie ({@code law_changed_at <= :today}) po ostatniej
     * odpowiedzi użytkownika, wracają jako zaległe (termin powtórki = {@code :now}), w obu trybach. Ruszamy tylko
     * stany z terminem w przyszłości i niezawieszone, więc ponowne wywołanie niczego nie zmienia, a pytanie
     * powtórzone po zmianie ({@code last_review_at >= law_changed_at}) już nie wraca. Pytania, których użytkownik
     * jeszcze nie rozwiązywał, są nowe niezależnie od zmiany. Zwiększa {@code version}, żeby równoległa odpowiedź
     * dostała konflikt zamiast po cichu nadpisać nowy termin. Zwraca liczbę przestawionych stanów.
     */
    @Modifying
    @Query(value = """
            UPDATE user_question_states
            SET due_date = :now, version = user_question_states.version + 1
            FROM questions q
            WHERE q.id = user_question_states.question_id
            AND q.law_changed_at IS NOT NULL
            AND q.law_changed_at <= :today
            AND user_question_states.suspended = false
            AND user_question_states.due_date > :now
            AND user_question_states.last_review_at < q.law_changed_at
            """, nativeQuery = true)
    int requeueAfterLawChange(@Param("now") LocalDateTime now, @Param("today") LocalDate today);

    /** Wszystkie stany użytkownika wraz z treścią pytania, do eksportu jego danych. */
    @Query("""
            SELECT s.question.id AS questionId, s.mode AS mode, s.question.body AS questionBody, s.state AS state,
                   s.reps AS reps, s.lapses AS lapses, s.stability AS stability, s.difficulty AS difficulty,
                   s.dueDate AS dueDate, s.lastReviewAt AS lastReviewAt, s.suspended AS suspended
            FROM UserQuestionState s
            WHERE s.user.id = :userId
            ORDER BY s.question.id, s.mode
            """)
    List<ExportedState> findExportRows(@Param("userId") Long userId);

    /** Stan nauki testowej w jednym źródle: utrwalone i z minionym terminem powtórki (bez zawieszonych). */
    interface SourceStates {
        Long getSourceId();

        Long getMature();

        Long getDue();
    }

    /**
     * Pytania testowe, które użytkownik już zaczął, w każdym źródle (arkuszu): ile jest utrwalonych (stabilność powyżej
     * progu) i ile ma już miniony termin powtórki. Pula taka sama jak w {@link #countDue} i {@link #countStartedByAct}.
     */
    @Query("""
            SELECT s.question.source.id AS sourceId,
                   SUM(CASE WHEN s.stability > :matureStability THEN 1 ELSE 0 END) AS mature,
                   SUM(CASE WHEN s.suspended = false AND s.dueDate <= :now THEN 1 ELSE 0 END) AS due
            FROM UserQuestionState s
            WHERE s.user.id = :userId AND s.mode = pl.gradusiuris.backend.review.ReviewMode.TEST
            AND s.reps > 0
            AND\s""" + PoolJpql.STATE + """
            
            GROUP BY s.question.source.id
            """)
    List<SourceStates> countTestStatesBySource(
            @Param("userId") Long userId,
            @Param("now") LocalDateTime now,
            @Param("matureStability") double matureStability,
            @Param("actIds") Collection<Long> actIds,
            @Param("sourceIds") Collection<Long> sourceIds
    );

    interface ExportedState {
        Long getQuestionId();

        ReviewMode getMode();

        String getQuestionBody();

        LearningState getState();

        Integer getReps();

        Integer getLapses();

        Double getStability();

        Double getDifficulty();

        LocalDateTime getDueDate();

        LocalDateTime getLastReviewAt();

        Boolean getSuspended();
    }

    interface DifficultRow {
        Long getQuestionId();

        ReviewMode getMode();

        String getBody();

        String getActName();

        Integer getLapses();

        LocalDateTime getSuspendedAt();
    }

    interface StartedSummary {
        Long getQuestionId();

        Long getLegalActId();

        Double getStability();

        Integer getLapses();

        LocalDateTime getLastReviewAt();

        ReviewMode getMode();
    }

    interface DueRow {
        Long getLegalActId();

        LocalDateTime getDueDate();
    }

    interface QuestionDue {
        Long getQuestionId();

        LocalDateTime getDueDate();

        Double getStability();

        LocalDateTime getLastReviewAt();

        Integer getLapses();

        Boolean getSuspended();
    }
}
