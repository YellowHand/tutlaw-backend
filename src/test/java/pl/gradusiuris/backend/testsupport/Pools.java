package pl.gradusiuris.backend.testsupport;

import org.mockito.Mockito;
import pl.gradusiuris.backend.pool.Pool;
import pl.gradusiuris.backend.pool.PoolService;
import pl.gradusiuris.backend.pool.SourceFilter;
import pl.gradusiuris.backend.pool.StudentCourses;
import pl.gradusiuris.backend.track.TrackKind;

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

    /**
     * Ta sama pula z innym filtrem źródeł: tak jak robi to prawdziwy {@code PoolService.forUser(userId, filter)}
     * ({@code sourceIds} to źródła dozwolone przez filtr; źródło 3 to pytania autorskie, źródła własne ścieżki
     * wskazuje {@code ownSheetIds}, pozostałe arkusze to „inne").
     */
    public static Pool withFilter(Pool base, SourceFilter filter) {
        List<Long> allowed = new ArrayList<>();
        for (Long source : ALL_SOURCES) {
            boolean ok;
            if (source == QuestionRefs.AUTHORED) {
                ok = filter.authored();
            } else if (base.ownSheetIds().contains(source)) {
                ok = filter.ownSheets();
            } else {
                ok = filter.otherSheets();
            }
            if (ok) {
                allowed.add(source);
            }
        }
        return new Pool(base.trackId(), base.trackKind(), base.baseActIds(), base.actIds(), base.narrowed(), filter,
                base.ownSheetIds(), allowed.isEmpty() ? List.of(Pool.NO_SUCH_ID) : allowed, base.allSourceIds());
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
                // forUser(userId, filter) liczy pulę z wybranych grup źródeł, forUser(userId) ze wszystkich
                return invocation.getArguments().length == 2
                        ? withFilter(pool.get(), (SourceFilter) invocation.getArgument(1))
                        : pool.get();
            }
            if (invocation.getMethod().getName().equals("studentCourses")) {
                return courses.get();
            }
            throw new UnsupportedOperationException("PoolService." + invocation.getMethod().getName());
        });
    }
}
