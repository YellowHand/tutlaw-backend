package pl.gradusiuris.backend.mark;

/**
 * Oznaczenie pytania, którego użytkownik nie widzi: służy do układania testów „Pytania z terminów" i „Pułapki".
 */
public enum QuestionMarkType {
    /** W pytaniu pada termin (np. do kiedy wnieść kasację), a odpowiedzi różnią się terminami albo liczbą dni. */
    TIME_LIMIT,
    /** Pułapka: przeczenie albo wyjątek („nie jest", „nieprawidłowe", „z wyjątkiem"). */
    TRAP_NEGATION,
    /** Pułapka: słowo absolutne w części odpowiedzi („zawsze", „nigdy", „wyłącznie", „jedynie"). */
    TRAP_ABSOLUTE,
    /** Pułapka: zbliżone liczby albo terminy w odpowiedziach (7 / 14 / 30 dni). */
    TRAP_NUMBERS,
    /** Pułapka: odpowiedzi różnią się spójnikiem („i" zamiast „lub"). */
    TRAP_CONJUNCTION,
    /** Pułapka: poprawna jest odpowiedź według nieaktualnego stanu prawnego. Nadaje się tylko ręcznie. */
    TRAP_OUTDATED_LAW;

    /** Czy to któryś z podtypów pułapki. */
    public boolean isTrap() {
        return name().startsWith("TRAP_");
    }
}
