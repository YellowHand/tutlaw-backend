package pl.tutlaw.backend.review;

/**
 * Które pytania z wybranych działów wchodzą do własnego testu. Pytania zawieszone i spoza wybranych pochodzeń
 * (filtr pochodzenia w Testach) nie wchodzą nigdy, niezależnie od rodzaju.
 */
public enum CustomTestKind {
    /** Wszystkie dostępne pytania. */
    ALL,
    /** Pytania, których użytkownik jeszcze nie rozwiązywał. */
    NEW,
    /** Pytania, w których ostatnia odpowiedź w teście była błędna (z ostatnich 30 dni): jak „Moje błędy". */
    MISTAKES,
    /** Pytania z minionym terminem powtórki. */
    DUE
}
