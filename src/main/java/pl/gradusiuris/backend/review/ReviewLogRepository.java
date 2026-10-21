package pl.gradusiuris.backend.review;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.gradusiuris.backend.common.GroupCount;
import pl.gradusiuris.backend.pool.PoolJpql;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ReviewLogRepository extends JpaRepository<ReviewLog, Long> {

    /**
     * Numery pierwszych odpowiedzi użytkownika w danym trybie (stronicowane): długość listy to liczba odpowiedzi
     * przycięta do rozmiaru strony. Do rozstrzygania "czy dał już 10 odpowiedzi" bez liczenia całej historii.
     */
    @Query("SELECT r.id FROM ReviewLog r WHERE r.user.id = :userId AND r.mode = :mode ORDER BY r.id")
    List<Long> findIdsByMode(@Param("userId") Long userId, @Param("mode") ReviewMode mode, Pageable pageable);

    long countByUserIdAndMode(Long userId, ReviewMode mode);

    /** Kasuje całą historię odpowiedzi użytkownika (zerowanie postępów). Zwraca liczbę usuniętych wpisów. */
    @Modifying
    @Query("DELETE FROM ReviewLog r WHERE r.user.id = :userId")
    int deleteAllByUser(@Param("userId") Long userId);

    /**
     * Średni czas odpowiedzi w sekundach w danym trybie. Pojedyncza odpowiedź liczy się najwyżej tyle, ile
     * {@link AnswerTimes#CAP_SECONDS} (pytanie zostawione otwarte nie psuje średniej); wpisy bez pomiaru pomijamy.
     * Null, gdy nie ma żadnego pomiaru.
     */
    @Query("""
            SELECT AVG(CASE WHEN r.timeSpentSeconds > 120.0D THEN 120.0D ELSE r.timeSpentSeconds END)
            FROM ReviewLog r
            WHERE r.user.id = :userId AND r.mode = :mode
            AND r.timeSpentSeconds IS NOT NULL AND r.timeSpentSeconds > 0
            """)
    Double averageAnswerSeconds(@Param("userId") Long userId, @Param("mode") ReviewMode mode);

    /**
     * Ile kart użytkownik zobaczył po raz pierwszy w danym przedziale czasu (w danym trybie).
     * Pierwsza powtórka to wpis bez {@code elapsedDays}.
     */
    @Query("""
            SELECT COUNT(r) FROM ReviewLog r
            WHERE r.user.id = :userId
            AND r.mode = :mode
            AND r.elapsedDays IS NULL
            AND r.reviewedAt >= :startOfDay
            AND r.reviewedAt < :endOfDay
            """)
    long countFirstReviewsBetween(
            @Param("userId") Long userId,
            @Param("mode") ReviewMode mode,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay
    );

    /**
     * Ile zaległych kart (nie nowych) użytkownik przerobił w danym przedziale czasu i trybie. Liczymy powtórki
     * po upływie doby od poprzedniej ({@code elapsedDays >= 1}); poprawki tej samej karty tego samego dnia
     * (np. po „Nie pamiętam") nie zużywają dziennego limitu.
     */
    @Query("""
            SELECT COUNT(r) FROM ReviewLog r
            WHERE r.user.id = :userId
            AND r.mode = :mode
            AND r.elapsedDays >= 1.0
            AND r.reviewedAt >= :startOfDay
            AND r.reviewedAt < :endOfDay
            """)
    long countDueReviewsBetween(
            @Param("userId") Long userId,
            @Param("mode") ReviewMode mode,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay
    );

    /** Same chwile odpowiedzi od podanego momentu (do mapy aktywności), bez ładowania całych wpisów. */
    @Query("""
            SELECT r.reviewedAt FROM ReviewLog r
            WHERE r.user.id = :userId AND r.reviewedAt >= :since
            """)
    List<LocalDateTime> findReviewTimesSince(
            @Param("userId") Long userId,
            @Param("since") LocalDateTime since
    );

    /** Chwile odpowiedzi w danym trybie (fiszki albo test) od podanego momentu, do wykresu aktywności. */
    @Query("""
            SELECT r.reviewedAt FROM ReviewLog r
            WHERE r.user.id = :userId AND r.mode = :mode AND r.reviewedAt >= :since
            """)
    List<LocalDateTime> findReviewTimesSinceByMode(
            @Param("userId") Long userId,
            @Param("mode") ReviewMode mode,
            @Param("since") LocalDateTime since
    );

    /** Ile odpowiedzi w danym trybie użytkownik dał od podanego momentu. */
    @Query("""
            SELECT COUNT(r) FROM ReviewLog r
            WHERE r.user.id = :userId AND r.mode = :mode AND r.reviewedAt >= :since
            """)
    long countAnswersSince(
            @Param("userId") Long userId,
            @Param("mode") ReviewMode mode,
            @Param("since") LocalDateTime since
    );

    /** Ile z nich było poprawnych ({@code wasCorrect} jest zapisywane tylko w testach). */
    @Query("""
            SELECT COUNT(r) FROM ReviewLog r
            WHERE r.user.id = :userId AND r.mode = :mode AND r.reviewedAt >= :since
            AND r.wasCorrect = true
            """)
    long countCorrectAnswersSince(
            @Param("userId") Long userId,
            @Param("mode") ReviewMode mode,
            @Param("since") LocalDateTime since
    );

    /**
     * „Pewne błędy": odpowiedź błędna, choć użytkownik NIE zaznaczył „Nie jestem pewien". To najgroźniejszy rodzaj
     * błędu, bo oznacza utrwalone błędne przekonanie, a nie lukę, o której użytkownik wie.
     */
    @Query("""
            SELECT COUNT(r) FROM ReviewLog r
            WHERE r.user.id = :userId AND r.mode = :mode AND r.reviewedAt >= :since
            AND r.wasCorrect = false AND r.wasGuessed = false
            """)
    long countConfidentMistakesSince(
            @Param("userId") Long userId,
            @Param("mode") ReviewMode mode,
            @Param("since") LocalDateTime since
    );

    /**
     * „Trafione w ciemno": odpowiedź poprawna, ale z zaznaczonym „Nie jestem pewien". Wynik wygląda dobrze,
     * a wiedza jest krucha, więc takie pytania warto powtarzać.
     */
    @Query("""
            SELECT COUNT(r) FROM ReviewLog r
            WHERE r.user.id = :userId AND r.mode = :mode AND r.reviewedAt >= :since
            AND r.wasCorrect = true AND r.wasGuessed = true
            """)
    long countLuckyGuessesSince(
            @Param("userId") Long userId,
            @Param("mode") ReviewMode mode,
            @Param("since") LocalDateTime since
    );

    /**
     * Liczba prawdziwych powtórek (odpowiedzi na pytanie widziane wcześniej w tym trybie, po co najmniej
     * {@code minElapsedDays} dniach) z oceną co najmniej {@code minRating}. Podstawa wskaźnika zapamiętywania:
     * z minRating = 1 to wszystkie takie powtórki, z minRating = 2 tylko udane.
     * Pierwsze zobaczenie pytania i powtórki tego samego dnia nie mówią nic o zapamiętywaniu, więc nie wchodzą.
     */
    @Query("""
            SELECT COUNT(r) FROM ReviewLog r
            WHERE r.user.id = :userId
            AND r.mode = :mode
            AND r.reviewedAt >= :since
            AND r.elapsedDays >= :minElapsedDays
            AND r.rating >= :minRating
            """)
    long countRecalls(
            @Param("userId") Long userId,
            @Param("mode") ReviewMode mode,
            @Param("since") LocalDateTime since,
            @Param("minElapsedDays") double minElapsedDays,
            @Param("minRating") int minRating
    );

    /** To samo, ograniczone do jednego aktu prawnego. */
    @Query("""
            SELECT COUNT(r) FROM ReviewLog r
            WHERE r.user.id = :userId
            AND r.mode = :mode
            AND r.question.legalAct.id = :actId
            AND r.reviewedAt >= :since
            AND r.elapsedDays >= :minElapsedDays
            AND r.rating >= :minRating
            """)
    long countRecallsInAct(
            @Param("userId") Long userId,
            @Param("mode") ReviewMode mode,
            @Param("actId") Long actId,
            @Param("since") LocalDateTime since,
            @Param("minElapsedDays") double minElapsedDays,
            @Param("minRating") int minRating
    );

    /**
     * Pytania, które użytkownik pomylił w teście, choć NIE zaznaczył „Nie jestem pewien" (pewny błąd).
     * Taki błąd odblokowuje fiszkę pytania (zob. QuestionScopes#isUnlocked). Same numery, bez duplikatów.
     */
    @Query("""
            SELECT DISTINCT r.question.id FROM ReviewLog r
            WHERE r.user.id = :userId
            AND r.mode = pl.gradusiuris.backend.review.ReviewMode.TEST
            AND r.wasCorrect = false AND r.wasGuessed = false
            """)
    List<Long> findConfidentMistakeQuestionIds(@Param("userId") Long userId);

    /** Ile razy użytkownik pomylił to pytanie w teście bez zaznaczenia „Nie jestem pewien". */
    @Query("""
            SELECT COUNT(r) FROM ReviewLog r
            WHERE r.user.id = :userId AND r.question.id = :questionId
            AND r.mode = pl.gradusiuris.backend.review.ReviewMode.TEST
            AND r.wasCorrect = false AND r.wasGuessed = false
            """)
    long countConfidentMistakes(@Param("userId") Long userId, @Param("questionId") Long questionId);

    /**
     * Ile różnych pytań użytkownik rozwiązał co najmniej raz w teście, w każdym akcie prawnym (groupId = numer aktu).
     * Liczą się tylko pytania z puli ({@code actIds}, {@code sourceIds}), żeby wynik nie przekraczał liczby pytań
     * dostępnych w katalogu.
     */
    @Query("""
            SELECT r.question.legalAct.id AS groupId, COUNT(DISTINCT r.question.id) AS total
            FROM ReviewLog r
            WHERE r.user.id = :userId
            AND r.mode = pl.gradusiuris.backend.review.ReviewMode.TEST
            AND\s""" + PoolJpql.LOG + """
            
            GROUP BY r.question.legalAct.id
            """)
    List<GroupCount> countSolvedTestQuestionsByAct(
            @Param("userId") Long userId,
            @Param("actIds") Collection<Long> actIds,
            @Param("sourceIds") Collection<Long> sourceIds
    );

    /** To samo, ale pogrupowane po źródle (arkuszu): groupId = numer źródła. */
    @Query("""
            SELECT r.question.source.id AS groupId, COUNT(DISTINCT r.question.id) AS total
            FROM ReviewLog r
            WHERE r.user.id = :userId
            AND r.mode = pl.gradusiuris.backend.review.ReviewMode.TEST
            AND\s""" + PoolJpql.LOG + """
            
            GROUP BY r.question.source.id
            """)
    List<GroupCount> countSolvedTestQuestionsBySource(
            @Param("userId") Long userId,
            @Param("actIds") Collection<Long> actIds,
            @Param("sourceIds") Collection<Long> sourceIds
    );

    /**
     * Odpowiedzi użytkownika w testach od podanego momentu, od najnowszej. Z tego wylicza się, w których pytaniach
     * ostatnia odpowiedź była błędna ("Powtórz moje błędy"), bez zapytania korelowanego na każde pytanie.
     */
    @Query("""
            SELECT r.question.id AS questionId, r.wasCorrect AS wasCorrect, r.reviewedAt AS reviewedAt
            FROM ReviewLog r
            WHERE r.user.id = :userId
            AND r.mode = pl.gradusiuris.backend.review.ReviewMode.TEST
            AND r.reviewedAt >= :since
            ORDER BY r.reviewedAt DESC, r.id DESC
            """)
    List<TestAnswerRow> findTestAnswersSince(
            @Param("userId") Long userId,
            @Param("since") LocalDateTime since,
            Pageable pageable
    );

    /** Cała historia odpowiedzi użytkownika, do eksportu jego danych. */
    @Query("""
            SELECT r.question.id AS questionId, r.reviewedAt AS reviewedAt, r.mode AS mode, r.rating AS rating,
                   r.wasCorrect AS wasCorrect, r.wasGuessed AS wasGuessed
            FROM ReviewLog r
            WHERE r.user.id = :userId
            ORDER BY r.reviewedAt, r.id
            """)
    List<ExportedReview> findExportRows(@Param("userId") Long userId);

    interface TestAnswerRow {
        Long getQuestionId();

        Boolean getWasCorrect();

        LocalDateTime getReviewedAt();
    }

    interface ExportedReview {
        Long getQuestionId();

        LocalDateTime getReviewedAt();

        ReviewMode getMode();

        Integer getRating();

        Boolean getWasCorrect();

        Boolean getWasGuessed();
    }

    /** Odpowiedzi w testach w jednym źródle (arkuszu): wszystkie, poprawne i pewne błędy (bez „Nie jestem pewien"). */
    interface SourceAnswers {
        Long getSourceId();

        Long getAnswers();

        Long getCorrect();

        Long getConfidentMistakes();
    }

    /**
     * Odpowiedzi w testach od podanego momentu, pogrupowane po źródle (arkuszu) pytania. Liczą się tylko pytania
     * z puli, tak jak w liczbie pytań i rozwiązanych.
     */
    @Query("""
            SELECT r.question.source.id AS sourceId,
                   COUNT(r) AS answers,
                   SUM(CASE WHEN r.wasCorrect = true THEN 1 ELSE 0 END) AS correct,
                   SUM(CASE WHEN r.wasCorrect = false AND r.wasGuessed = false THEN 1 ELSE 0 END) AS confidentMistakes
            FROM ReviewLog r
            WHERE r.user.id = :userId
            AND r.mode = pl.gradusiuris.backend.review.ReviewMode.TEST
            AND r.reviewedAt >= :since
            AND\s""" + PoolJpql.LOG + """
            
            GROUP BY r.question.source.id
            """)
    List<SourceAnswers> countTestAnswersBySource(
            @Param("userId") Long userId,
            @Param("since") LocalDateTime since,
            @Param("actIds") Collection<Long> actIds,
            @Param("sourceIds") Collection<Long> sourceIds
    );
}
