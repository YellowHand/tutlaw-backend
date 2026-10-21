package pl.gradusiuris.backend.pool;

import pl.gradusiuris.backend.track.TrackKind;

import java.util.List;

/**
 * Pula pytań użytkownika, czyli to, co wolno mu widzieć, wyliczone przez {@link PoolService}. Zapytania dostają ją
 * jako dwie listy numerów: aktów prawnych ({@link #actIds()}) i źródeł ({@link #sourceIds()}); pytanie należy do puli,
 * gdy jego akt i jego źródło są na tych listach. Obie listy są zawsze niepuste (gdy nic nie pasuje, zawierają numer,
 * którego nie ma w bazie), bo pusta lista w {@code IN} bywa różnie obsługiwana.
 *
 * @param trackId      wybrana ścieżka ({@code null}: użytkownik jeszcze nie wybrał, pula obejmuje wszystko)
 * @param trackKind    rodzaj ścieżki ({@code null}, gdy nie wybrano)
 * @param baseActIds   akty ścieżki bez zawężenia użytkownika (akty z jej listy oraz akty pytań z jej arkuszy)
 * @param actIds       akty w mocy: zawężone przez użytkownika albo, bez zawężenia, jak {@code baseActIds}
 * @param narrowed     czy użytkownik zawęził pulę do wybranych aktów
 * @param filter       filtr źródeł wybrany przez użytkownika
 * @param ownSheetIds  arkusze wybranej ścieżki
 * @param sourceIds    źródła dozwolone przez filtr (dla testów)
 * @param allSourceIds wszystkie źródła (fiszki ignorują filtr źródeł, bo wynikają z błędów w testach)
 */
public record Pool(
        Long trackId,
        TrackKind trackKind,
        List<Long> baseActIds,
        List<Long> actIds,
        boolean narrowed,
        SourceFilter filter,
        List<Long> ownSheetIds,
        List<Long> sourceIds,
        List<Long> allSourceIds
) {

    /** Numer, którego nie ma w bazie: zamiast pustej listy w {@code IN}. */
    public static final Long NO_SUCH_ID = -1L;

    /** Pula dla fiszek: te same akty, ale bez filtra źródeł. */
    public Pool forFlashcards() {
        return new Pool(trackId, trackKind, baseActIds, actIds, narrowed, filter, ownSheetIds, allSourceIds, allSourceIds);
    }

    /** Ta sama pula bez zawężenia użytkownika do aktów (egzamin próbny losuje z całej puli ścieżki). */
    public Pool unnarrowed() {
        return new Pool(trackId, trackKind, baseActIds, baseActIds, false, filter, ownSheetIds, sourceIds, allSourceIds);
    }

    public boolean isStudent() {
        return trackKind == TrackKind.STUDENT;
    }

    static List<Long> orNoSuchId(java.util.Collection<Long> ids) {
        return ids.isEmpty() ? List.of(NO_SUCH_ID) : List.copyOf(ids);
    }
}
