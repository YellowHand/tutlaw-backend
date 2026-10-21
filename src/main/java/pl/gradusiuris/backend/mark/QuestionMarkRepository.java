package pl.gradusiuris.backend.mark;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pl.gradusiuris.backend.question.CorrectOption;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface QuestionMarkRepository extends JpaRepository<QuestionMark, Long> {

    /**
     * Numery pytań z zatwierdzonym oznaczeniem któregokolwiek z podanych rodzajów. To jedyne oznaczenia, które liczą
     * się w testach: propozycje i odrzucone nie wchodzą.
     */
    @Query("""
            SELECT DISTINCT m.questionId FROM QuestionMark m
            WHERE m.status = pl.gradusiuris.backend.mark.MarkStatus.CONFIRMED AND m.mark IN :marks
            """)
    List<Long> findConfirmedQuestionIds(@Param("marks") Collection<QuestionMarkType> marks);

    /** Wszystkie oznaczenia podanych pytań, niezależnie od stanu (widok pytań w panelu administratora). */
    @Query("SELECT m FROM QuestionMark m WHERE m.questionId IN :questionIds ORDER BY m.questionId, m.mark")
    List<QuestionMark> findAllByQuestionIds(@Param("questionIds") Collection<Long> questionIds);

    Optional<QuestionMark> findByQuestionIdAndMark(Long questionId, QuestionMarkType mark);

    /** Wszystkie istniejące pary (pytanie, oznaczenie), niezależnie od stanu: automat ich nie powtarza. */
    @Query("SELECT m.questionId AS questionId, m.mark AS mark FROM QuestionMark m")
    List<MarkKey> findAllKeys();

    /** Liczba oznaczeń w każdym stanie, dla każdego rodzaju. */
    @Query("""
            SELECT m.mark AS mark, m.status AS status, COUNT(m) AS total
            FROM QuestionMark m
            GROUP BY m.mark, m.status
            """)
    List<MarkCount> countByMarkAndStatus();

    /**
     * Zmienia stan wszystkich oznaczeń w stanie {@code from} (dla podanych rodzajów) na {@code to} i zapisuje, kto i kiedy
     * zdecydował. Zwraca liczbę zmienionych. Nie wolno wracać do propozycji (baza tego nie przyjmie), pilnuje tego serwis.
     */
    @Modifying
    @Query("""
            UPDATE QuestionMark m
            SET m.status = :toStatus, m.reviewedAt = :now, m.reviewedBy = :reviewer
            WHERE m.status = :fromStatus AND m.mark IN :marks
            """)
    int changeStatus(
            @Param("fromStatus") MarkStatus from,
            @Param("toStatus") MarkStatus to,
            @Param("marks") Collection<QuestionMarkType> marks,
            @Param("now") LocalDateTime now,
            @Param("reviewer") Long reviewer);

    /**
     * Oznaczenia w danym stanie (opcjonalnie tylko jednego rodzaju) z pytaniem do przejrzenia, od najstarszego numeru
     * pytania. {@code marks} jest zawsze niepuste (wołający podaje wszystkie rodzaje, gdy nie filtruje).
     */
    @Query(value = """
            SELECT m.id AS id, m.questionId AS questionId, m.mark AS mark, m.origin AS origin, m.status AS status,
                   q.body AS body, q.optionA AS optionA, q.optionB AS optionB, q.optionC AS optionC,
                   q.correctOption AS correctOption, s.name AS sourceName, a.shortName AS actShortName
            FROM QuestionMark m
            JOIN Question q ON q.id = m.questionId
            JOIN q.source s
            JOIN q.legalAct a
            WHERE m.status = :status AND m.mark IN :marks
            ORDER BY m.questionId, m.id
            """,
            countQuery = """
            SELECT COUNT(m) FROM QuestionMark m
            WHERE m.status = :status AND m.mark IN :marks
            """)
    Page<MarkedQuestion> findForReview(
            @Param("status") MarkStatus status,
            @Param("marks") Collection<QuestionMarkType> marks,
            Pageable pageable);

    interface MarkKey {
        Long getQuestionId();

        QuestionMarkType getMark();
    }

    interface MarkCount {
        QuestionMarkType getMark();

        MarkStatus getStatus();

        Long getTotal();
    }

    interface MarkedQuestion {
        Long getId();

        Long getQuestionId();

        QuestionMarkType getMark();

        MarkOrigin getOrigin();

        MarkStatus getStatus();

        String getBody();

        String getOptionA();

        String getOptionB();

        String getOptionC();

        CorrectOption getCorrectOption();

        String getSourceName();

        String getActShortName();
    }
}
