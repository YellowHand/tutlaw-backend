package pl.tutlaw.backend.question;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.tutlaw.backend.common.GroupCount;
import pl.tutlaw.backend.pool.PoolJpql;

import java.util.Collection;
import java.util.List;

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
}
