package pl.tutlaw.backend.common;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;

/** Sortowanie nazw po polsku (ą, ć, ę, ł, ń, ó, ś, ź, ż w swoich miejscach alfabetu). */
public final class PolishSort {

    private static final Collator COLLATOR = createCollator();

    private PolishSort() {
    }

    private static Collator createCollator() {
        Collator collator = Collator.getInstance(Locale.forLanguageTag("pl-PL"));
        // SECONDARY: rozróżnia litery z ogonkami, ale nie wielkość liter.
        collator.setStrength(Collator.SECONDARY);
        return collator;
    }

    /** Komparator napisów wg polskiego alfabetu, bez względu na wielkość liter (compare Collatora jest synchronizowany). */
    public static Comparator<String> byName() {
        return (a, b) -> COLLATOR.compare(a == null ? "" : a, b == null ? "" : b);
    }
}
