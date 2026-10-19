package pl.tutlaw.backend.pool;

import java.util.ArrayList;
import java.util.List;

/**
 * Filtr źródeł pytań w Testach: z których z trzech grup użytkownik chce pytań (własne arkusze, arkusze innych
 * aplikacji, pytania autorskie). Co najmniej jedna grupa jest zawsze wybrana.
 */
public record SourceFilter(boolean ownSheets, boolean otherSheets, boolean authored) {

    public static final SourceFilter ALL = new SourceFilter(true, true, true);

    /** Kody grup w stałej kolejności (tak zapisujemy filtr w zapisanym własnym teście). */
    public static final String OWN_SHEETS = "OWN_SHEETS";
    public static final String OTHER_SHEETS = "OTHER_SHEETS";
    public static final String AUTHORED = "AUTHORED";

    public boolean isAll() {
        return ownSheets && otherSheets && authored;
    }

    public boolean isNone() {
        return !ownSheets && !otherSheets && !authored;
    }

    /** Zapis: lista kodów oddzielona przecinkami; wszystkie grupy to {@code null} (stan domyślny). */
    public String serialize() {
        if (isAll()) {
            return null;
        }
        List<String> codes = new ArrayList<>();
        if (ownSheets) codes.add(OWN_SHEETS);
        if (otherSheets) codes.add(OTHER_SHEETS);
        if (authored) codes.add(AUTHORED);
        return String.join(",", codes);
    }

    /**
     * Odczyt zapisanej listy. Pusta wartość albo taka, w której nie ma ani jednego znanego kodu, daje wszystkie grupy
     * (filtr nigdy nie może ukryć całego materiału). Nieznane kody są pomijane.
     */
    public static SourceFilter parse(String stored) {
        if (stored == null || stored.isBlank()) {
            return ALL;
        }
        boolean own = false;
        boolean other = false;
        boolean authored = false;
        for (String code : stored.split(",")) {
            switch (code.trim()) {
                case OWN_SHEETS -> own = true;
                case OTHER_SHEETS -> other = true;
                case AUTHORED -> authored = true;
                default -> {
                    // stary albo uszkodzony kod: pomijamy
                }
            }
        }
        SourceFilter parsed = new SourceFilter(own, other, authored);
        return parsed.isNone() ? ALL : parsed;
    }
}
