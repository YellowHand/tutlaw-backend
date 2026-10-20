package pl.tutlaw.backend.testsupport;

import org.mockito.Mockito;
import pl.tutlaw.backend.pool.Pool;
import pl.tutlaw.backend.pool.PoolService;
import pl.tutlaw.backend.pool.SourceFilter;
import pl.tutlaw.backend.pool.StudentCourses;
import pl.tutlaw.backend.track.TrackKind;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Gotowe pule do testów. Źródła: 1 = własny arkusz, 2 = arkusz innej ścieżki, 3 = pytania autorskie
 * (zob. {@link QuestionRefs}).
 */
public final class Pools {

    public static final List<Long> ALL_SOURCES = List.of(QuestionRefs.OWN_SHEET, QuestionRefs.OTHER_SHEET, QuestionRefs.AUTHORED);

    private Pools() {
    }

    /** Pula ścieżki z podanymi aktami (bez zawężenia) i filtrem źródeł. */
    public static Pool of(SourceFilter filter, long... actIds) {
        List<Long> acts = new ArrayList<>();
        for (long id : actIds) {
            acts.add(id);
        }
        List<Long> allowed = new ArrayList<>();
        if (filter.ownSheets()) allowed.add(QuestionRefs.OWN_SHEET);
        if (filter.otherSheets()) allowed.add(QuestionRefs.OTHER_SHEET);
        if (filter.authored()) allowed.add(QuestionRefs.AUTHORED);
        return new Pool(1L, TrackKind.EXAM, acts, acts, false, filter, List.of(QuestionRefs.OWN_SHEET), allowed, ALL_SOURCES);
    }

    /** Pula obejmująca akty 1 i 2 oraz wszystkie źródła. */
    public static Pool all() {
        return of(SourceFilter.ALL, 1, 2);
    }

    /** Zawężenie do podanych aktów (z puli {@code base}). */
    public static Pool narrowedTo(Pool base, long... actIds) {
        List<Long> acts = new ArrayList<>();
        for (long id : actIds) {
            acts.add(id);
        }
        return new Pool(base.trackId(), base.trackKind(), base.baseActIds(), acts, true, base.filter(),
                base.ownSheetIds(), base.sourceIds(), base.allSourceIds());
    }

    /** Pula Studenta (ścieżka 9) z podanymi aktami, bez zawężenia i z wszystkimi źródłami. */
    public static Pool student(long... actIds) {
        List<Long> acts = new ArrayList<>();
        for (long id : actIds) {
            acts.add(id);
        }
        return new Pool(9L, TrackKind.STUDENT, acts, acts, false, SourceFilter.ALL, List.of(), ALL_SOURCES, ALL_SOURCES);
    }

    /** Atrapa serwisu puli, która zawsze oddaje bieżącą wartość z {@code pool} (test może ją zmieniać w trakcie). */
    public static PoolService holding(Supplier<Pool> pool) {
        return holding(pool, () -> {
            throw new UnsupportedOperationException("PoolService.studentCourses");
        });
    }

    /** Jak {@link #holding(Supplier)}, z przedmiotami Studenta oddawanymi z {@code courses}. */
    public static PoolService holding(Supplier<Pool> pool, Supplier<StudentCourses> courses) {
        return Mockito.mock(PoolService.class, invocation -> {
            if (invocation.getMethod().getName().equals("forUser")) {
                return pool.get();
            }
            if (invocation.getMethod().getName().equals("studentCourses")) {
                return courses.get();
            }
            throw new UnsupportedOperationException("PoolService." + invocation.getMethod().getName());
        });
    }
}
