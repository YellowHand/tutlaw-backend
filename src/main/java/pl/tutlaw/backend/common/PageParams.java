package pl.tutlaw.backend.common;

/**
 * Bezpieczne parametry stronicowania z adresu (?page=0&size=50): brak wartości daje domyślne, a wartości spoza
 * zakresu są przycinane (strona od 0, rozmiar od 1 do {@link #MAX_SIZE}), zamiast kończyć się błędem 400/500.
 */
public record PageParams(int page, int size) {

    public static final int DEFAULT_SIZE = 50;
    public static final int MAX_SIZE = 200;

    public static PageParams of(Integer page, Integer size) {
        int safePage = page == null ? 0 : Math.max(0, page);
        int safeSize = size == null ? DEFAULT_SIZE : Math.min(MAX_SIZE, Math.max(1, size));
        return new PageParams(safePage, safeSize);
    }
}
