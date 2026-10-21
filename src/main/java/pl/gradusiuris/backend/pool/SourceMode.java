package pl.gradusiuris.backend.pool;

/**
 * Z jakiego źródła mają pochodzić pytania jednego testu (wybór w kreatorze Testów, osobny przy każdym teście):
 * <ul>
 *   <li>{@link #ALL}: z wszystkich źródeł,</li>
 *   <li>{@link #MINISTRY}: tylko z arkuszy egzaminacyjnych Ministerstwa Sprawiedliwości (własne arkusze ścieżki i arkusze
 *       innych ścieżek),</li>
 *   <li>{@link #AUTHORED}: tylko pytania autorskie dodane w aplikacji.</li>
 * </ul>
 * Pod spodem to {@link SourceFilter}: tryb jest po prostu jego trzema najczęstszymi ustawieniami, dzięki czemu cała
 * reguła puli ({@code PoolService}) i zapis w zapisanym teście zostają bez zmian.
 */
public enum SourceMode {
    ALL(SourceFilter.ALL),
    MINISTRY(new SourceFilter(true, true, false)),
    AUTHORED(new SourceFilter(false, false, true));

    private final SourceFilter filter;

    SourceMode(SourceFilter filter) {
        this.filter = filter;
    }

    public SourceFilter filter() {
        return filter;
    }

    /** Brak wyboru (null) to wszystkie źródła. */
    public static SourceMode orAll(SourceMode mode) {
        return mode == null ? ALL : mode;
    }

    /**
     * Tryb odpowiadający zapisanemu filtrowi. Test zapisany w starszych wersjach mógł mieć filtr z innego zestawu grup:
     * same arkusze (w tym tylko własne) to {@link #MINISTRY}, same pytania autorskie to {@link #AUTHORED}, a każda
     * mieszanka z pytaniami autorskimi to {@link #ALL}.
     */
    public static SourceMode fromFilter(SourceFilter filter) {
        if (filter.authored() && !filter.ownSheets() && !filter.otherSheets()) {
            return AUTHORED;
        }
        if (!filter.authored() && (filter.ownSheets() || filter.otherSheets())) {
            return MINISTRY;
        }
        return ALL;
    }
}
