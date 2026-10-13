package pl.tutlaw.backend.card;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.tutlaw.backend.common.GroupCount;

import java.util.Collection;
import java.util.List;

public interface CardRepository extends JpaRepository<Card, Long> {

    List<Card> findByChapterId(Long chapterId);

    List<Card> findBySourceId(Long sourceId);

    /** Czy do tego pytania testowego jest powiązana choć jedna widoczna (nieukryta) fiszka. */
    boolean existsByLinkedTestCardIdAndHiddenFalse(Long linkedTestCardId);

    /** Numery kart powiązanych z pytaniem (jego fiszek: automatycznej albo napisanej ręcznie). */
    @Query("SELECT c.id FROM Card c WHERE c.linkedTestCardId = :questionId")
    List<Long> findIdsByLinkedTestCardId(@Param("questionId") Long questionId);

    /** Strona kart rozdziału (lista dla administratora). Sortowanie podaje wywołujący w {@code pageable}. */
    Page<Card> findByChapterId(Long chapterId, Pageable pageable);

    /** Strona kart źródła (lista dla administratora). */
    Page<Card> findBySourceId(Long sourceId, Pageable pageable);

    /**
     * Odchudzony widok karty do układania kolejek, planów i losowania egzaminu: samo id, kolejność rozdziału
     * (null, gdy karta nie należy do rozdziału) i poprawna opcja (null, gdy to fiszka bez formatu testowego).
     * Bez treści pytań, bo przy tysiącach kart i kilkudziesięciu pytaniach w sesji ładowanie całych kart
     * tylko po to, żeby większość z nich odrzucić, byłoby marnotrawstwem.
     */
    interface CardRef {
        Long getId();

        Integer getChapterOrder();

        CorrectOption getCorrectOption();

        /** Tylko fiszka powiązana z pytaniem testowym: numer tego pytania (inaczej null). */
        Long getLinkedTestCardId();

        /** Pochodzenie karty (filtr pochodzenia w Testach). */
        QuestionOrigin getOrigin();
    }

    @Query("""
            SELECT c.id AS id, ch.orderIndex AS chapterOrder, c.correctOption AS correctOption,
                   c.linkedTestCardId AS linkedTestCardId, c.origin AS origin
            FROM Card c LEFT JOIN c.chapter ch
            WHERE c.source.subject.id = :subjectId
            AND c.hidden = false
            AND (
                c.source.sourceType = pl.tutlaw.backend.source.SourceType.FLAT_POOL
                OR (c.chapter IS NOT NULL AND c.chapter.id IN (
                    SELECT p.chapter.id FROM ChapterProgress p
                    WHERE p.user.id = :userId AND p.read = true
                ))
            )
            """)
    List<CardRef> findReadableRefsForUserAndSubject(
            @Param("subjectId") Long subjectId,
            @Param("userId") Long userId
    );

    @Query("""
            SELECT c.id AS id, ch.orderIndex AS chapterOrder, c.correctOption AS correctOption,
                   c.linkedTestCardId AS linkedTestCardId, c.origin AS origin
            FROM Card c LEFT JOIN c.chapter ch
            WHERE c.source.id = :sourceId
            AND c.hidden = false
            AND (
                c.source.sourceType = pl.tutlaw.backend.source.SourceType.FLAT_POOL
                OR (c.chapter IS NOT NULL AND c.chapter.id IN (
                    SELECT p.chapter.id FROM ChapterProgress p
                    WHERE p.user.id = :userId AND p.read = true
                ))
            )
            """)
    List<CardRef> findReadableRefsForUserAndSource(
            @Param("sourceId") Long sourceId,
            @Param("userId") Long userId
    );

    /**
     * Wszystkie karty dostępne dla użytkownika we wszystkich przedmiotach
     * (pule płaskie oraz rozdziały oznaczone jako przeczytane).
     * Używane przez globalną kolejkę powtórek.
     */
    @Query("""
            SELECT c.id AS id, ch.orderIndex AS chapterOrder, c.correctOption AS correctOption,
                   c.linkedTestCardId AS linkedTestCardId, c.origin AS origin
            FROM Card c LEFT JOIN c.chapter ch
            WHERE c.hidden = false
            AND (
                c.source.sourceType = pl.tutlaw.backend.source.SourceType.FLAT_POOL
                OR (c.chapter IS NOT NULL AND c.chapter.id IN (
                    SELECT p.chapter.id FROM ChapterProgress p
                    WHERE p.user.id = :userId AND p.read = true
                ))
            )
            """)
    List<CardRef> findReadableRefsForUser(@Param("userId") Long userId);

    /**
     * Pełne karty o podanych numerach, ze źródłem pobranym od razu (mapowanie odpowiedzi pokazuje jego nazwę).
     * Kolejność jest dowolna: kto jej potrzebuje, układa ją sam (zob. {@link CardScopes#loadInOrder}).
     */
    @Query("SELECT c FROM Card c JOIN FETCH c.source WHERE c.id IN :ids")
    List<Card> findAllWithSourceByIdIn(@Param("ids") Collection<Long> ids);

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

    /**
     * Liczba kart w każdym rozdziale źródła (groupId = id rozdziału). Fiszki (withFlashcards) i pytania testowe
     * (withTests) wybiera się osobno. Fiszka powiązana z pytaniem testowym liczy się dopiero po pierwszym pewnym
     * błędzie w tym pytaniu (ta sama zasada co w CardScopes#isUnlocked), żeby mianownik nie zawierał ukrytych kart.
     */
    @Query("""
            SELECT c.chapter.id AS groupId, COUNT(c) AS total
            FROM Card c
            WHERE c.source.id = :sourceId AND c.chapter IS NOT NULL
            AND c.hidden = false
            AND ((c.correctOption IS NULL AND :withFlashcards = true
                  AND (c.linkedTestCardId IS NULL
                       OR EXISTS (
                        SELECT r.id FROM ReviewLog r
                        WHERE r.user.id = :userId AND r.card.id = c.linkedTestCardId
                        AND r.mode = pl.tutlaw.backend.review.ReviewMode.TEST
                        AND r.wasCorrect = false AND r.wasGuessed = false)
                       OR EXISTS (
                        SELECT a.id FROM FlashcardAddition a
                        WHERE a.userId = :userId AND a.questionId = c.linkedTestCardId)))
                 OR (c.correctOption IS NOT NULL AND :withTests = true))
            GROUP BY c.chapter.id
            """)
    List<GroupCount> countByChapterForSource(
            @Param("sourceId") Long sourceId,
            @Param("userId") Long userId,
            @Param("withFlashcards") boolean withFlashcards,
            @Param("withTests") boolean withTests
    );

    /** Liczba kart w każdym źródle przedmiotu (groupId = id źródła); zasady wyboru jak w {@link #countByChapterForSource}. */
    @Query("""
            SELECT c.source.id AS groupId, COUNT(c) AS total
            FROM Card c
            WHERE c.source.subject.id = :subjectId
            AND c.hidden = false
            AND ((c.correctOption IS NULL AND :withFlashcards = true
                  AND (c.linkedTestCardId IS NULL
                       OR EXISTS (
                        SELECT r.id FROM ReviewLog r
                        WHERE r.user.id = :userId AND r.card.id = c.linkedTestCardId
                        AND r.mode = pl.tutlaw.backend.review.ReviewMode.TEST
                        AND r.wasCorrect = false AND r.wasGuessed = false)
                       OR EXISTS (
                        SELECT a.id FROM FlashcardAddition a
                        WHERE a.userId = :userId AND a.questionId = c.linkedTestCardId)))
                 OR (c.correctOption IS NOT NULL AND :withTests = true))
            GROUP BY c.source.id
            """)
    List<GroupCount> countBySourceForSubject(
            @Param("subjectId") Long subjectId,
            @Param("userId") Long userId,
            @Param("withFlashcards") boolean withFlashcards,
            @Param("withTests") boolean withTests
    );

    /** Wszystkie pytania testowe (karty z poprawną odpowiedzią) w każdym źródle (groupId = id źródła). */
    @Query("""
            SELECT c.source.id AS groupId, COUNT(c) AS total
            FROM Card c
            WHERE c.correctOption IS NOT NULL AND c.hidden = false
            GROUP BY c.source.id
            """)
    List<GroupCount> countTestQuestionsBySource();

    /** Pytania testowe dostępne dla użytkownika w każdym źródle (pula płaska albo przeczytany rozdział). */
    @Query("""
            SELECT c.source.id AS groupId, COUNT(c) AS total
            FROM Card c
            WHERE c.correctOption IS NOT NULL AND c.hidden = false
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
            AND c.hidden = false
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

    /**
     * Pytania testowe o wskazanych numerach, dostępne dla użytkownika (pula płaska albo przeczytany rozdział).
     * W odróżnieniu od {@link #findPracticeTestCards} nie pomija zawieszonych: służy do sprawdzania egzaminu,
     * w którym pytania zostały już pokazane. Źródło pobierane od razu (raport pokazuje jego nazwę).
     */
    @Query("""
            SELECT c FROM Card c JOIN FETCH c.source
            WHERE c.id IN :ids
            AND c.correctOption IS NOT NULL
            AND (
                c.source.sourceType = pl.tutlaw.backend.source.SourceType.FLAT_POOL
                OR (c.chapter IS NOT NULL AND c.chapter.id IN (
                    SELECT p.chapter.id FROM ChapterProgress p
                    WHERE p.user.id = :userId AND p.read = true
                ))
            )
            """)
    List<Card> findReadableTestCardsByIds(@Param("ids") Collection<Long> ids, @Param("userId") Long userId);

    /** Dostępne karty użytkownika w jednej kategorii (rodzaju), do testu lub nauki z całego działu. */
    @Query("""
            SELECT c.id AS id, ch.orderIndex AS chapterOrder, c.correctOption AS correctOption,
                   c.linkedTestCardId AS linkedTestCardId, c.origin AS origin
            FROM Card c LEFT JOIN c.chapter ch
            WHERE c.source.subject.category.id = :categoryId
            AND c.hidden = false
            AND (
                c.source.sourceType = pl.tutlaw.backend.source.SourceType.FLAT_POOL
                OR (c.chapter IS NOT NULL AND c.chapter.id IN (
                    SELECT p.chapter.id FROM ChapterProgress p
                    WHERE p.user.id = :userId AND p.read = true
                ))
            )
            """)
    List<CardRef> findReadableRefsForUserAndCategory(
            @Param("categoryId") Long categoryId,
            @Param("userId") Long userId
    );

    /** Liczba pytań testowych (bez ukrytych) w każdym pochodzeniu: do okna wyboru filtru pochodzenia. */
    interface OriginCount {
        QuestionOrigin getOrigin();

        Long getTotal();
    }

    @Query("""
            SELECT c.origin AS origin, COUNT(c) AS total
            FROM Card c
            WHERE c.correctOption IS NOT NULL AND c.hidden = false
            GROUP BY c.origin
            """)
    List<OriginCount> countTestQuestionsByOrigin();

    /** Jak {@link #countTestQuestionsBySource()}, ale tylko pytania z wybranych pochodzeń (katalog Testów). */
    @Query("""
            SELECT c.source.id AS groupId, COUNT(c) AS total
            FROM Card c
            WHERE c.correctOption IS NOT NULL AND c.hidden = false
            AND c.origin IN :origins
            GROUP BY c.source.id
            """)
    List<GroupCount> countTestQuestionsBySourceInOrigins(@Param("origins") Collection<QuestionOrigin> origins);

    /** Jak {@link #countReadableTestQuestionsBySource}, ale tylko pytania z wybranych pochodzeń (katalog Testów). */
    @Query("""
            SELECT c.source.id AS groupId, COUNT(c) AS total
            FROM Card c
            WHERE c.correctOption IS NOT NULL AND c.hidden = false
            AND c.origin IN :origins
            AND (
                c.source.sourceType = pl.tutlaw.backend.source.SourceType.FLAT_POOL
                OR (c.chapter IS NOT NULL AND c.chapter.id IN (
                    SELECT p.chapter.id FROM ChapterProgress p
                    WHERE p.user.id = :userId AND p.read = true
                ))
            )
            GROUP BY c.source.id
            """)
    List<GroupCount> countReadableTestQuestionsBySourceInOrigins(
            @Param("userId") Long userId,
            @Param("origins") Collection<QuestionOrigin> origins
    );
}
