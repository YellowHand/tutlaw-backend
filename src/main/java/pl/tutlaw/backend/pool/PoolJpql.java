package pl.tutlaw.backend.pool;

/**
 * Wspólne warunki puli w zapytaniach JPQL, żeby ta sama reguła nie była przepisywana w kilkudziesięciu miejscach.
 * Parametry: {@code :actIds} i {@code :sourceIds} (listy z {@link Pool}, zawsze niepuste).
 *
 * <p>Aliasy: {@code q} to {@code Question}, {@code s} to {@code UserQuestionState}, {@code r} to {@code ReviewLog}.
 */
public final class PoolJpql {

    private PoolJpql() {
    }

    /** Pytanie widoczne w puli (testowej, gdy źródła zawężone filtrem; fiszkowej, gdy {@code sourceIds} to wszystkie). */
    public static final String QUESTION =
            "q.hidden = false AND q.legalAct.id IN :actIds AND q.source.id IN :sourceIds";

    /** To samo dla stanu nauki: pytanie stanu jest w puli, a fiszka nie jest ukryta. */
    public static final String STATE =
            "s.question.hidden = false AND s.question.legalAct.id IN :actIds AND s.question.source.id IN :sourceIds"
                    + " AND (s.mode = pl.tutlaw.backend.review.ReviewMode.TEST OR s.question.flashcardHidden = false)";

    /** To samo dla wpisu historii odpowiedzi. */
    public static final String LOG =
            "r.question.hidden = false AND r.question.legalAct.id IN :actIds AND r.question.source.id IN :sourceIds";
}
