package pl.tutlaw.backend.common;

/**
 * Bezpieczne parametry stronicowania z adresu (?page=0&size=50): brak wartości daje domyślne, a wartości spoza
 * zakresu są przycinane (strona od 0, rozmiar od 1 do {@link #MAX_SIZE}), zamiast kończyć się błędem 400/500.
 */
public record PageParams(int page, int size) {

    public static final int DEFAULT_SIZE = 50;
    public static final int MAX_SIZE = 200;
    /** Listy historii użytkownika (egzaminy, własne testy): domyślnie 20 na stronę, najwyżej 100. */
    public static final int LIST_DEFAULT_SIZE = 20;
    public static final int LIST_MAX_SIZE = 100;

    public static PageParams of(Integer page, Integer size) {
        return of(page, size, DEFAULT_SIZE, MAX_SIZE);
    }

    /** Stronicowanie list historii użytkownika ({@link #LIST_DEFAULT_SIZE} na stronę, najwyżej {@link #LIST_MAX_SIZE}). */
    public static PageParams ofList(Integer page, Integer size) {
        return of(page, size, LIST_DEFAULT_SIZE, LIST_MAX_SIZE);
    }

    private static PageParams of(Integer page, Integer size, int defaultSize, int maxSize) {
        int safePage = page == null ? 0 : Math.max(0, page);
        int safeSize = size == null ? defaultSize : Math.min(maxSize, Math.max(1, size));
        return new PageParams(safePage, safeSize);
    }
}
