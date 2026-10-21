package pl.tutlaw.backend.question;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.tutlaw.backend.common.GroupCount;
import pl.tutlaw.backend.mark.MarkStatus;
import pl.tutlaw.backend.mark.QuestionMarkType;
import pl.tutlaw.backend.pool.PoolJpql;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Zapytania o pytania. Pula użytkownika ({@link PoolJpql#QUESTION}) to dwie listy numerów: {@code actIds} i
 * {@code sourceIds} (zawsze niepuste, zob. {@code Pool}).
 */
public interface QuestionRepository extends JpaRepository<Question, Long> {

    /**
     * Odchudzony widok pytań puli do układania kolejek, planów i losowania. Bez treści, bo przy tysiącach pytań
     * i kilkudziesięciu w sesji ładowanie całych pytań tylko po to, żeby większość odrzucić, byłoby marnotrawstwem.
     */
    @Query("""
            SELECT q.id AS id, q.legalAct.id AS legalActId, q.legalAct.legalField.id AS legalFieldId,
                   q.source.id AS sourceId, q.flashcardHidden AS flashcardHidden, q.lawChangedAt AS lawChangedAt
            FROM Question q
            WHERE\s""" + PoolJpql.QUESTION)
    List<QuestionRef> findRefsInPool(
            @Param("actIds") Collection<Long> actIds,
            @Param("sourceIds") Collection<Long> sourceIds
    );

    /**
     * Widoczne pytania z podanych źródeł (arkuszy), od najstarszego numeru, czyli w kolejności wczytania arkusza. Bez
     * ograniczenia do aktów i bez filtra puli: to cały arkusz, tak jak był na egzaminie (egzamin z wybranego roku).
     */
    @Query("""
            SELECT q.id AS id, q.legalAct.id AS legalActId, q.legalAct.legalField.id AS legalFieldId,
                   q.source.id AS sourceId, q.flashcardHidden AS flashcardHidden, q.lawChangedAt AS lawChangedAt
            FROM Question q
            WHERE q.hidden = false AND q.source.id IN :sourceIds
            ORDER BY q.id""")
    List<QuestionRef> findRefsInSources(@Param("sourceIds") Collection<Long> sourceIds);

    /**
     * Treści pytań o numerach większych od podanego, od najmniejszego numeru (do analizy oznaczeń partiami: całą tabelę
     * z treściami ładuje się po kawałku, a nie naraz). Liczbę pytań w partii ogranicza {@code pageable}.
     */
    @Query("""
            SELECT q.id AS id, q.body AS body, q.optionA AS optionA, q.optionB AS optionB, q.optionC AS optionC
            FROM Question q
            WHERE q.id > :afterId
            ORDER BY q.id""")
    List<QuestionText> findTextsAfter(@Param("afterId") Long afterId, Pageable pageable);

    /** Numery aktów, do których należą pytania z podanych źródeł (akty własnych arkuszy ścieżki). */
    @Query("SELECT DISTINCT q.legalAct.id FROM Question q WHERE q.source.id IN :sourceIds")
    List<Long> findActIdsInSources(@Param("sourceIds") Collection<Long> sourceIds);

    /**
     * Pełne pytania o podanych numerach z aktem prawnym, jego dziedziną i źródłem pobranymi od razu (mapowanie
     * odpowiedzi pokazuje ich nazwy). Kolejność jest dowolna: kto jej potrzebuje, układa ją sam
     * (zob. {@link QuestionScopes#loadInOrder}).
     */
    @Query("""
            SELECT q FROM Question q
            JOIN FETCH q.source
            JOIN FETCH q.legalAct a
            JOIN FETCH a.legalField
            WHERE q.id IN :ids
            """)
    List<Question> findAllWithRelationsByIdIn(@Param("ids") Collection<Long> ids);

    /** Pytania puli w każdym akcie prawnym (groupId = numer aktu). */
    @Query("""
            SELECT q.legalAct.id AS groupId, COUNT(q) AS total
            FROM Question q
            WHERE\s""" + PoolJpql.QUESTION + """
            
            GROUP BY q.legalAct.id
            """)
    List<GroupCount> countByAct(
            @Param("actIds") Collection<Long> actIds,
            @Param("sourceIds") Collection<Long> sourceIds
    );

    /**
     * Pytania puli w każdym rozdziale aktu (groupId = numer rozdziału). Pytania bez rozdziału nie liczą się nigdzie:
     * są tylko w akcie jako całości.
     */
    @Query("""
            SELECT q.section.id AS groupId, COUNT(q) AS total
            FROM Question q
            WHERE\s""" + PoolJpql.QUESTION + """

            AND q.section IS NOT NULL
            GROUP BY q.section.id
            """)
    List<GroupCount> countBySection(
            @Param("actIds") Collection<Long> actIds,
            @Param("sourceIds") Collection<Long> sourceIds
    );

    /** Numery widocznych pytań z podanych rozdziałów (do zawężenia własnego testu do rozdziałów). */
    @Query("SELECT q.id FROM Question q WHERE q.hidden = false AND q.section.id IN :sectionIds")
    List<Long> findIdsBySectionIds(@Param("sectionIds") Collection<Long> sectionIds);

    /** Pytania puli w każdym źródle (groupId = numer źródła). */
    @Query("""
            SELECT q.source.id AS groupId, COUNT(q) AS total
            FROM Question q
            WHERE\s""" + PoolJpql.QUESTION + """
            
            GROUP BY q.source.id
            """)
    List<GroupCount> countBySource(
            @Param("actIds") Collection<Long> actIds,
            @Param("sourceIds") Collection<Long> sourceIds
    );

    /**
     * Fiszki odblokowane dla użytkownika w każdym akcie prawnym (groupId = numer aktu): pytania puli z widoczną fiszką,
     * które użytkownik pomylił pewnie w teście albo dodał do fiszek ręcznie (ta sama zasada co w
     * {@link QuestionScopes#isUnlocked}), żeby mianownik nie zawierał niewidocznych fiszek.
     */
    @Query("""
            SELECT q.legalAct.id AS groupId, COUNT(q) AS total
            FROM Question q
            WHERE q.hidden = false AND q.flashcardHidden = false
            AND q.legalAct.id IN :actIds AND q.source.id IN :sourceIds
            AND (EXISTS (
                    SELECT r.id FROM ReviewLog r
                    WHERE r.user.id = :userId AND r.question.id = q.id
                    AND r.mode = pl.tutlaw.backend.review.ReviewMode.TEST
                    AND r.wasCorrect = false AND r.wasGuessed = false)
                 OR EXISTS (
                    SELECT a.id FROM FlashcardAddition a
                    WHERE a.userId = :userId AND a.questionId = q.id))
            GROUP BY q.legalAct.id
            """)
    List<GroupCount> countUnlockedFlashcardsByAct(
            @Param("userId") Long userId,
            @Param("actIds") Collection<Long> actIds,
            @Param("sourceIds") Collection<Long> sourceIds
    );

    /**
     * Pytania do ćwiczenia wskazanych numerów: tylko te z puli aktów użytkownika, nieukryte i NIE zawieszone przez
     * niego w testach. Źródło i akt pobierane od razu, bo kolejka pokazuje ich nazwy. Filtra źródeł nie stosujemy:
     * sesje (własny test, powtórka błędów) składają się z pytań wybranych wcześniej, a zmiana filtra w trakcie nie
     * powinna ich podmieniać.
     */
    @Query("""
            SELECT q FROM Question q
            JOIN FETCH q.source
            JOIN FETCH q.legalAct a
            JOIN FETCH a.legalField
            WHERE q.id IN :ids
            AND q.hidden = false
            AND q.legalAct.id IN :actIds
            AND NOT EXISTS (
                SELECT s.id FROM UserQuestionState s
                WHERE s.user.id = :userId AND s.question.id = q.id
                AND s.mode = pl.tutlaw.backend.review.ReviewMode.TEST AND s.suspended = true
            )
            """)
    List<Question> findPracticeQuestions(
            @Param("ids") Collection<Long> ids,
            @Param("userId") Long userId,
            @Param("actIds") Collection<Long> actIds
    );

    /**
     * Pytania o wskazanych numerach do sprawdzenia egzaminu: bez sprawdzania ukrycia i zawieszenia, bo pytania zostały
     * już pokazane w egzaminie. Źródło i akt pobierane od razu (raport pokazuje ich nazwy).
     */
    @Query("""
            SELECT q FROM Question q
            JOIN FETCH q.source
            JOIN FETCH q.legalAct a
            JOIN FETCH a.legalField
            WHERE q.id IN :ids
            """)
    List<Question> findForGrading(@Param("ids") Collection<Long> ids);

    // ------------------------------------------------------------------------------------------ panel administratora

    /** Liczba pytań w grupie i ile z nich jest ukrytych (zliczane są także pytania ukryte, bo to widok administratora). */
    interface GroupTotals {

        Long getGroupId();

        Long getTotal();

        Long getHiddenTotal();
    }

    /** Wszystkie pytania (także ukryte) w każdym akcie prawnym. */
    @Query("""
            SELECT q.legalAct.id AS groupId, COUNT(q) AS total,
                   SUM(CASE WHEN q.hidden = true THEN 1 ELSE 0 END) AS hiddenTotal
            FROM Question q
            GROUP BY q.legalAct.id
            """)
    List<GroupTotals> totalsByAct();

    /** Wszystkie pytania (także ukryte) w każdym źródle (arkuszu). */
    @Query("""
            SELECT q.source.id AS groupId, COUNT(q) AS total,
                   SUM(CASE WHEN q.hidden = true THEN 1 ELSE 0 END) AS hiddenTotal
            FROM Question q
            GROUP BY q.source.id
            """)
    List<GroupTotals> totalsBySource();

    /** Wszystkie pytania (także ukryte) w każdym rozdziale; pytania bez rozdziału nie liczą się nigdzie. */
    @Query("""
            SELECT q.section.id AS groupId, COUNT(q) AS total,
                   SUM(CASE WHEN q.hidden = true THEN 1 ELSE 0 END) AS hiddenTotal
            FROM Question q
            WHERE q.section IS NOT NULL
            GROUP BY q.section.id
            """)
    List<GroupTotals> totalsBySection();

    /** Ile pytań (także ukrytych) ma akt: akt z pytaniami nie może zostać usunięty. */
    @Query("SELECT COUNT(q) FROM Question q WHERE q.legalAct.id = :actId")
    long countInAct(@Param("actId") Long actId);

    /** Ile pytań (także ukrytych) ma źródło: arkusz z pytaniami nie może zostać usunięty. */
    @Query("SELECT COUNT(q) FROM Question q WHERE q.source.id = :sourceId")
    long countInSource(@Param("sourceId") Long sourceId);

    /** Ile pytań (także ukrytych) ma rozdział: rozdział z pytaniami nie może zostać usunięty. */
    @Query("SELECT COUNT(q) FROM Question q WHERE q.section.id = :sectionId")
    long countInSection(@Param("sectionId") Long sectionId);

    /** Ile jest wszystkich ukrytych pytań (do przeglądu panelu). */
    @Query("SELECT COUNT(q) FROM Question q WHERE q.hidden = true")
    long countHidden();

    /**
     * Lista pytań w panelu administratora (także ukrytych), od najnowszego numeru. Każdy filtr ma parę „flaga i wartość"
     * (np. {@code anyAct} i {@code actId}), bo parametr z wartością pustą (null) bywa w bazie kłopotliwy: gdy flaga
     * mówi „bez ograniczenia", wartość jest obojętna. {@code pattern} to wzorzec LIKE z małymi literami (znak ucieczki
     * to {@code !}); szukamy w treści, odpowiedziach i podstawie prawnej, a {@code idMatch} dopuszcza też numer pytania.
     * Filtr oznaczenia to pytania z oznaczeniem danego rodzaju i stanu; filtr zgłoszeń to pytania z otwartym zgłoszeniem.
     */
    @Query(value = """
            SELECT q FROM Question q
            JOIN FETCH q.legalAct a
            JOIN FETCH q.source s
            LEFT JOIN FETCH q.section sec
            WHERE (q.id = :idMatch
                   OR lower(q.body) LIKE :pattern ESCAPE '!'
                   OR lower(q.optionA) LIKE :pattern ESCAPE '!'
                   OR lower(q.optionB) LIKE :pattern ESCAPE '!'
                   OR lower(q.optionC) LIKE :pattern ESCAPE '!'
                   OR lower(coalesce(q.legalBasis, '')) LIKE :pattern ESCAPE '!')
            AND (:anyAct = true OR a.id = :actId)
            AND (:anySection = true OR sec.id = :sectionId)
            AND (:withoutSection = false OR sec IS NULL)
            AND (:anySource = true OR s.id = :sourceId)
            AND (:anyVisibility = true OR q.hidden = :hidden)
            AND (:onlyReported = false OR EXISTS (
                    SELECT r.id FROM QuestionReport r
                    WHERE r.question.id = q.id AND r.status = pl.tutlaw.backend.report.QuestionReportStatus.NEW))
            AND (:anyMark = true OR EXISTS (
                    SELECT m.id FROM QuestionMark m
                    WHERE m.questionId = q.id AND m.mark = :mark AND m.status = :markStatus))
            ORDER BY q.id DESC
            """,
            countQuery = """
            SELECT COUNT(q) FROM Question q
            LEFT JOIN q.section sec
            WHERE (q.id = :idMatch
                   OR lower(q.body) LIKE :pattern ESCAPE '!'
                   OR lower(q.optionA) LIKE :pattern ESCAPE '!'
                   OR lower(q.optionB) LIKE :pattern ESCAPE '!'
                   OR lower(q.optionC) LIKE :pattern ESCAPE '!'
                   OR lower(coalesce(q.legalBasis, '')) LIKE :pattern ESCAPE '!')
            AND (:anyAct = true OR q.legalAct.id = :actId)
            AND (:anySection = true OR sec.id = :sectionId)
            AND (:withoutSection = false OR sec IS NULL)
            AND (:anySource = true OR q.source.id = :sourceId)
            AND (:anyVisibility = true OR q.hidden = :hidden)
            AND (:onlyReported = false OR EXISTS (
                    SELECT r.id FROM QuestionReport r
                    WHERE r.question.id = q.id AND r.status = pl.tutlaw.backend.report.QuestionReportStatus.NEW))
            AND (:anyMark = true OR EXISTS (
                    SELECT m.id FROM QuestionMark m
                    WHERE m.questionId = q.id AND m.mark = :mark AND m.status = :markStatus))
            """)
    Page<Question> findForAdmin(
            @Param("pattern") String pattern,
            @Param("idMatch") Long idMatch,
            @Param("anyAct") boolean anyAct,
            @Param("actId") Long actId,
            @Param("anySection") boolean anySection,
            @Param("sectionId") Long sectionId,
            @Param("withoutSection") boolean withoutSection,
            @Param("anySource") boolean anySource,
            @Param("sourceId") Long sourceId,
            @Param("anyVisibility") boolean anyVisibility,
            @Param("hidden") boolean hidden,
            @Param("onlyReported") boolean onlyReported,
            @Param("anyMark") boolean anyMark,
            @Param("mark") QuestionMarkType mark,
            @Param("markStatus") MarkStatus markStatus,
            Pageable pageable);

    /** Pytanie z aktem, rozdziałem i źródłem pobranymi od razu (edycja w panelu). */
    @Query("""
            SELECT q FROM Question q
            JOIN FETCH q.legalAct
            JOIN FETCH q.source
            LEFT JOIN FETCH q.section
            WHERE q.id = :id
            """)
    Optional<Question> findForAdminById(@Param("id") Long id);

    /** Treści pytań z podanych źródeł (także ukrytych): do wykrywania powtórzeń przy dodawaniu i imporcie. */
    @Query("""
            SELECT q.id AS id, q.source.id AS sourceId, q.body AS body
            FROM Question q
            WHERE q.source.id IN :sourceIds
            """)
    List<QuestionBody> findBodiesInSources(@Param("sourceIds") Collection<Long> sourceIds);
}
