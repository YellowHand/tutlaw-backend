package pl.tutlaw.backend.admin;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Adresy (slug) i porównywanie nazw w panelu administratora: małe litery bez polskich znaków, odstępy zamienione na
 * myślniki. Adres powstaje raz, przy dodaniu pozycji, i potem się nie zmienia (używają go skrypty wczytujące pytania).
 */
final class Slugs {

    private static final String FALLBACK = "pozycja";

    private Slugs() {
    }

    /** Małe litery, bez znaków diakrytycznych (ą→a, ł→l): do porównywania nazw i wyszukiwania. */
    static String fold(String text) {
        if (text == null) {
            return "";
        }
        String lower = text.toLowerCase(Locale.ROOT).replace('ł', 'l');
        return Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }

    /** Adres z nazwy, nie dłuższy niż {@code maxLength}. */
    static String of(String text, int maxLength) {
        String slug = fold(text).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.isEmpty()) {
            slug = FALLBACK;
        }
        if (slug.length() > maxLength) {
            slug = slug.substring(0, maxLength).replaceAll("-+$", "");
        }
        return slug;
    }

    /** Adres z nazwy, a gdy zajęty, z kolejnym numerem (-2, -3, ...), nadal w granicy {@code maxLength}. */
    static String unique(String text, int maxLength, Predicate<String> taken) {
        String base = of(text, maxLength);
        if (!taken.test(base)) {
            return base;
        }
        for (int i = 2; ; i++) {
            String suffix = "-" + i;
            String head = base.length() + suffix.length() > maxLength ? base.substring(0, maxLength - suffix.length()) : base;
            String candidate = head + suffix;
            if (!taken.test(candidate)) {
                return candidate;
            }
        }
    }
}
