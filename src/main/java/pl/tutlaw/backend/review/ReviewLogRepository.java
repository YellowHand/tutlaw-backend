package pl.tutlaw.backend.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.tutlaw.backend.common.GroupCount;

import java.time.LocalDateTime;
import java.util.List;

public interface ReviewLogRepository extends JpaRepository<ReviewLog, Long> {

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
     * Liczba prawdziwych powtórek (odpowiedzi na kartę widzianą wcześniej, po co najmniej {@code minElapsedDays}
     * dniach) z oceną co najmniej {@code minRating}. Podstawa wskaźnika zapamiętywania:
     * z minRating = 1 to wszystkie takie powtórki, z minRating = 2 tylko udane.
     * Pierwsze zobaczenie karty i powtórki tego samego dnia nie mówią nic o zapamiętywaniu, więc nie wchodzą.
     */
    @Query("""
            SELECT COUNT(r) FROM ReviewLog r
            WHERE r.user.id = :userId
            AND r.reviewedAt >= :since
            AND r.elapsedDays >= :minElapsedDays
            AND r.rating >= :minRating
            """)
    long countRecalls(
            @Param("userId") Long userId,
            @Param("since") LocalDateTime since,
            @Param("minElapsedDays") double minElapsedDays,
            @Param("minRating") int minRating
    );

    @Query("""
            SELECT COUNT(r) FROM ReviewLog r
            WHERE r.user.id = :userId
            AND r.card.source.subject.id = :subjectId
            AND r.reviewedAt >= :since
            AND r.elapsedDays >= :minElapsedDays
            AND r.rating >= :minRating
            """)
    long countRecallsInSubject(
            @Param("userId") Long userId,
            @Param("subjectId") Long subjectId,
            @Param("since") LocalDateTime since,
            @Param("minElapsedDays") double minElapsedDays,
            @Param("minRating") int minRating
    );

    /**
     * Ile różnych pytań użytkownik rozwiązał co najmniej raz w teście, w każdym źródle (groupId = id źródła).
     * Liczą się tylko pytania, do których ma teraz dostęp, żeby wynik nie przekraczał liczby dostępnych pytań.
     */
    @Query("""
            SELECT r.card.source.id AS groupId, COUNT(DISTINCT r.card.id) AS total
            FROM ReviewLog r
            WHERE r.user.id = :userId
            AND r.mode = pl.tutlaw.backend.review.ReviewMode.TEST
            AND (
                r.card.source.sourceType = pl.tutlaw.backend.source.SourceType.FLAT_POOL
                OR (r.card.chapter IS NOT NULL AND r.card.chapter.id IN (
                    SELECT p.chapter.id FROM ChapterProgress p
                    WHERE p.user.id = :userId AND p.read = true
                ))
            )
            GROUP BY r.card.source.id
            """)
    List<GroupCount> countSolvedTestQuestionsBySource(@Param("userId") Long userId);

    /** Cała historia odpowiedzi użytkownika, do eksportu jego danych. */
    @Query("""
            SELECT r.card.id AS cardId, r.reviewedAt AS reviewedAt, r.mode AS mode, r.rating AS rating,
                   r.wasCorrect AS wasCorrect, r.wasGuessed AS wasGuessed
            FROM ReviewLog r
            WHERE r.user.id = :userId
            ORDER BY r.reviewedAt, r.id
            """)
    List<ExportedReview> findExportRows(@Param("userId") Long userId);

    interface ExportedReview {
        Long getCardId();

        LocalDateTime getReviewedAt();

        ReviewMode getMode();

        Integer getRating();

        Boolean getWasCorrect();

        Boolean getWasGuessed();
    }
}
