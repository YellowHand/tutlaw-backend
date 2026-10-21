package pl.tutlaw.backend.review;

/**
 * Które pytania z wybranych działów wchodzą do własnego testu. Pytania zawieszone i spoza puli użytkownika
 * (akty jego ścieżki, wybrane źródło) nie wchodzą nigdy, niezależnie od rodzaju.
 */
public enum CustomTestKind {
    /** Wszystkie dostępne pytania. */
    ALL,
    /** Pytania, których użytkownik jeszcze nie rozwiązywał. */
    NEW,
    /** Pytania, w których ostatnia odpowiedź w teście była błędna (z ostatnich 30 dni): jak „Moje błędy". */
    MISTAKES,
    /** Pytania z minionym terminem powtórki. */
    DUE,
    /** Pytania ze zmianą w prawie z ostatniego roku (zob. {@code LawChangePolicy}). */
    LAW_CHANGE,
    /** Pytania o terminy: administrator zatwierdził oznaczenie {@code TIME_LIMIT} (zob. pakiet {@code mark}). */
    TIME_LIMIT,
    /** Pułapki: administrator zatwierdził któreś z oznaczeń {@code TRAP_*} (zob. pakiet {@code mark}). */
    TRAPS
}
