package pl.tutlaw.backend.mark;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Automat, który PROPONUJE oznaczenia pytania na podstawie jego treści i odpowiedzi. Niczego nie rozstrzyga: wynik
 * trafia do administratora jako propozycja ({@link MarkStatus#SUGGESTED}), a w testach liczą się dopiero zatwierdzone
 * oznaczenia. Dlatego reguły są ostrożne (lepiej pominąć pytanie, niż zalać administratora fałszywymi trafieniami).
 *
 * <p>Czysta klasa bez bazy: każda reguła jest opisana niżej i ma test. {@link QuestionMarkType#TRAP_OUTDATED_LAW}
 * nie da się rozpoznać z treści, więc automat go nie proponuje (nadaje się tylko ręcznie).
 *
 * <ul>
 *   <li><b>TIME_LIMIT</b>: w treści albo odpowiedziach jest liczba dni albo tygodni (np. „14 dni", „dwa tygodnie") albo
 *       samo słowo o terminie („termin", „w ciągu", „najpóźniej", „upływa", „przedawni…", „zawity", „prekluzyjny").
 *       Lata, miesiące i godziny liczą się dopiero razem ze słowem o terminie: „do 5 lat pozbawienia wolności" i „8 godzin
 *       odpoczynku" to nie terminy.</li>
 *   <li><b>TRAP_NEGATION</b>: w TREŚCI jest wyraźne przeczenie lub wyjątek („nie jest", „nieprawidłowe", „nieprawdziwe", „z wyjątkiem",
 *       „niedopuszczalne", „które z poniższych … nie", a „błędne" i „fałszywe" tylko w poleceniu typu „wskaż zdanie
 *       fałszywe", bo „fałszywe zeznanie" to pojęcie prawne).</li>
 *   <li><b>TRAP_ABSOLUTE</b>: słowo absolutne („zawsze", „nigdy", „wyłącznie", „jedynie", „w każdym przypadku",
 *       „w żadnym przypadku") jest w jednej albo dwóch odpowiedziach z trzech: wtedy odróżnia je od pozostałych. Gdy jest
 *       we wszystkich albo w żadnej, niczego nie rozstrzyga. „Bezwzględnie" pomijamy: „nieważność bezwzględna" to termin
 *       prawniczy, a nie słowo absolutne.</li>
 *   <li><b>TRAP_NUMBERS</b>: co najmniej dwie odpowiedzi mają liczby z tą samą jednostką (dni, tygodni, miesięcy, lat,
 *       godzin, %, zł), ale różne (np. 7 / 14 / 30 dni).</li>
 *   <li><b>TRAP_CONJUNCTION</b>: dwie odpowiedzi różnią się tylko spójnikiem: „i / oraz" zamiast „lub / albo".</li>
 * </ul>
 */
public final class QuestionMarkDetector {

    private static final Locale PL = Locale.forLanguageTag("pl-PL");

    private QuestionMarkDetector() {
    }

    /**
     * Oznaczenia, które automat proponuje dla pytania. Nigdy nie zawiera {@link QuestionMarkType#TRAP_OUTDATED_LAW}.
     * Brakujące (null) teksty traktuje jak puste.
     */
    public static Set<QuestionMarkType> detect(String body, String optionA, String optionB, String optionC) {
        String bodyText = normalize(body);
        List<String> options = List.of(normalize(optionA), normalize(optionB), normalize(optionC));
        Set<QuestionMarkType> marks = EnumSet.noneOf(QuestionMarkType.class);
        if (isTimeLimit(bodyText, options)) {
            marks.add(QuestionMarkType.TIME_LIMIT);
        }
        if (isNegation(bodyText)) {
            marks.add(QuestionMarkType.TRAP_NEGATION);
        }
        if (isAbsolute(options)) {
            marks.add(QuestionMarkType.TRAP_ABSOLUTE);
        }
        if (isNumbers(options)) {
            marks.add(QuestionMarkType.TRAP_NUMBERS);
        }
        if (isConjunction(options)) {
            marks.add(QuestionMarkType.TRAP_CONJUNCTION);
        }
        return marks;
    }

    // ------------------------------------------------------------------ normalizacja

    /** Małe litery, pojedyncze spacje, bez nowych linii; null to pusty tekst. */
    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(PL).replace(' ', ' ').replaceAll("\\s+", " ").trim();
    }

    // ------------------------------------------------------------------ liczby z jednostką

    /** Jednostki, które porównujemy. */
    enum Unit { DAYS, WEEKS, MONTHS, YEARS, HOURS, PERCENT, ZLOTY }

    /** Liczba z jednostką znaleziona w tekście, np. „14 dni". Liczba w postaci znormalizowanej („14", „2.5"). */
    record Quantity(String value, Unit unit) {
    }

    private static final Map<String, String> NUMBER_WORDS = new LinkedHashMap<>();

    static {
        // Rdzenie odmienionych liczebników (mianownik i dopełniacz), wystarczające dla terminów w przepisach.
        String[][] words = {
                {"jeden", "1"}, {"jednego", "1"}, {"jednym", "1"}, {"dwa", "2"}, {"dwóch", "2"}, {"dwu", "2"},
                {"trzy", "3"}, {"trzech", "3"}, {"cztery", "4"}, {"czterech", "4"}, {"pięć", "5"}, {"pięciu", "5"},
                {"sześć", "6"}, {"sześciu", "6"}, {"siedem", "7"}, {"siedmiu", "7"}, {"osiem", "8"}, {"ośmiu", "8"},
                {"dziewięć", "9"}, {"dziewięciu", "9"}, {"dziesięć", "10"}, {"dziesięciu", "10"},
                {"jedenaście", "11"}, {"jedenastu", "11"}, {"dwanaście", "12"}, {"dwunastu", "12"},
                {"czternaście", "14"}, {"czternastu", "14"}, {"piętnaście", "15"}, {"piętnastu", "15"},
                {"dwadzieścia", "20"}, {"dwudziestu", "20"}, {"trzydzieści", "30"}, {"trzydziestu", "30"},
                {"sześćdziesiąt", "60"}, {"sześćdziesięciu", "60"}, {"dziewięćdziesiąt", "90"},
                {"dziewięćdziesięciu", "90"},
        };
        for (String[] pair : words) {
            NUMBER_WORDS.put(pair[0], pair[1]);
        }
    }

    private static final String NUMBER_WORD_ALTERNATION = String.join("|", NUMBER_WORDS.keySet());

    /** Liczba (cyframi albo słowem), potem jednostka. Jednostki w różnych formach gramatycznych. */
    private static final Pattern QUANTITY = Pattern.compile(
            "(?<![\\p{L}\\d])(\\d+(?:[.,]\\d+)?|" + NUMBER_WORD_ALTERNATION + ")\\s*"
                    + "(dni|dniach|dnia|dniu|dzień|tygodni|tygodnie|tygodnia|tygodniach|tydzień|tygodniu|"
                    + "miesięcy|miesiące|miesiąca|miesiącu|miesiącach|miesiąc|"
                    + "lat|lata|latach|roku|rok|"
                    + "godzin|godziny|godzinie|godzinę|godzina|godzinach|"
                    + "%|procent\\p{L}*|"
                    + "zł|złotych|złote|złotego)(?![\\p{L}\\d])");

    /** Wszystkie liczby z jednostką z jednego tekstu. */
    static List<Quantity> quantities(String text) {
        List<Quantity> found = new ArrayList<>();
        Matcher matcher = QUANTITY.matcher(text);
        while (matcher.find()) {
            String number = numberValue(matcher.group(1));
            Unit unit = unitOf(matcher.group(2));
            if (number != null && unit != null) {
                found.add(new Quantity(number, unit));
            }
        }
        return found;
    }

    private static String numberValue(String raw) {
        String word = NUMBER_WORDS.get(raw);
        if (word != null) {
            return word;
        }
        try {
            return new BigDecimal(raw.replace(',', '.')).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Unit unitOf(String raw) {
        if (raw.startsWith("%") || raw.startsWith("procent")) {
            return Unit.PERCENT;
        }
        if (raw.startsWith("zł")) {
            return Unit.ZLOTY;
        }
        if (raw.startsWith("dn") || raw.startsWith("dzień")) {
            return Unit.DAYS;
        }
        if (raw.startsWith("tygod") || raw.startsWith("tydz")) {
            return Unit.WEEKS;
        }
        if (raw.startsWith("miesi")) {
            return Unit.MONTHS;
        }
        if (raw.equals("lat") || raw.startsWith("lata") || raw.startsWith("latach") || raw.startsWith("rok")) {
            return Unit.YEARS;
        }
        if (raw.startsWith("godzin")) {
            return Unit.HOURS;
        }
        return null;
    }

    // ------------------------------------------------------------------ TIME_LIMIT

    /** Słowa, po których poznajemy, że pytanie dotyczy terminu (inflekcje w regule, bez „terminal", „terminologia"). */
    private static final Pattern TIME_KEYWORD = Pattern.compile(
            "(?<![\\p{L}])(termin(?:ie|u|y|ów|em|owi|ach|ami)?|w ciągu|najpóźniej|nie później niż|"
                    + "upływ(?:a|u|em|ie|ają|ać)|po upływie|przedawni\\p{L}*|zawit\\p{L}*|prekluzyj\\p{L}*)(?![\\p{L}])");

    private static boolean isTimeLimit(String body, List<String> options) {
        List<String> texts = new ArrayList<>(options);
        texts.add(body);
        boolean keyword = texts.stream().anyMatch(t -> TIME_KEYWORD.matcher(t).find());
        if (keyword) {
            return true;
        }
        // Bez słowa o terminie wystarczą dni i tygodnie. Lata i miesiące to bez niego najczęściej kara („do 5 lat
        // pozbawienia wolności") albo wiek, a godziny to czas pracy czy odpoczynku, nie termin.
        return texts.stream().flatMap(t -> quantities(t).stream())
                .anyMatch(q -> q.unit() == Unit.DAYS || q.unit() == Unit.WEEKS);
    }

    // ------------------------------------------------------------------ TRAP_NEGATION

    /**
     * „Błędne" i „fałszywe" liczą się tylko w poleceniu („wskaż zdanie fałszywe", „które jest błędne"): „fałszywe zeznanie"
     * czy „błędne oświadczenie woli" to pojęcia prawne, a nie przeczenie.
     */
    private static final Pattern NEGATION = Pattern.compile(
            "(?<![\\p{L}])(nie jest|nie są|nieprawidłow\\p{L}*|niepoprawn\\p{L}*|nieprawdziw\\p{L}*|"
                    + "z wyjątkiem|wyjąwszy|niedopuszczaln\\p{L}*)(?![\\p{L}])"
                    + "|(?<![\\p{L}])(?:wskaż|zaznacz|wybierz|uznaj|które|który|która|jakie|jaka|jaki|odpowiedź)(?![\\p{L}])"
                    + "[^.?!]{0,60}(?<![\\p{L}])(?:błędn|fałszyw)\\p{L}*"
                    + "|poniższ\\p{L}*[^.?!]{0,60}(?<![\\p{L}])nie(?![\\p{L}])");

    private static boolean isNegation(String body) {
        return NEGATION.matcher(body).find();
    }

    // ------------------------------------------------------------------ TRAP_ABSOLUTE

    private static final Pattern ABSOLUTE = Pattern.compile(
            "(?<![\\p{L}])(zawsze|nigdy|wyłącznie|jedynie|bezwarunkowo|absolutnie|każdorazowo|"
                    + "w każdym przypadku|w każdym wypadku|w żadnym przypadku|w żadnym wypadku)(?![\\p{L}])");

    private static boolean isAbsolute(List<String> options) {
        long withAbsolute = options.stream().filter(o -> ABSOLUTE.matcher(o).find()).count();
        return withAbsolute >= 1 && withAbsolute < options.size();
    }

    // ------------------------------------------------------------------ TRAP_NUMBERS

    private static boolean isNumbers(List<String> options) {
        // Jednostka -> zbiory liczb z odpowiedzi, które tę jednostkę zawierają.
        Map<Unit, List<Set<String>>> byUnit = new HashMap<>();
        for (String option : options) {
            Map<Unit, Set<String>> inOption = new HashMap<>();
            for (Quantity quantity : quantities(option)) {
                inOption.computeIfAbsent(quantity.unit(), u -> new HashSet<>()).add(quantity.value());
            }
            inOption.forEach((unit, numbers) -> byUnit.computeIfAbsent(unit, u -> new ArrayList<>()).add(numbers));
        }
        for (List<Set<String>> sets : byUnit.values()) {
            if (sets.size() >= 2 && sets.stream().anyMatch(s -> !s.equals(sets.get(0)))) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ TRAP_CONJUNCTION

    private static final Set<String> AND_WORDS = Set.of("i", "oraz");
    private static final Set<String> OR_WORDS = Set.of("lub", "albo", "bądź");
    private static final String CONJUNCTION_PLACEHOLDER = "§";
    /** Progu podobieństwa słów użyto tylko, gdy odpowiedzi różnią się spójnikiem, a nie tylko spójnikiem. */
    private static final double SIMILARITY = 0.75;

    private static boolean isConjunction(List<String> options) {
        List<List<String>> tokens = options.stream().map(QuestionMarkDetector::tokens).toList();
        for (int i = 0; i < tokens.size(); i++) {
            for (int j = i + 1; j < tokens.size(); j++) {
                if (differOnlyByConjunction(tokens.get(i), tokens.get(j))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Słowa odpowiedzi bez znaków interpunkcyjnych (cyfry i litery zostają). */
    static List<String> tokens(String text) {
        String cleaned = text.replaceAll("[^\\p{L}\\p{N}§]+", " ").trim();
        return cleaned.isEmpty() ? List.of() : Arrays.asList(cleaned.split(" "));
    }

    private static boolean differOnlyByConjunction(List<String> left, List<String> right) {
        boolean leftAnd = left.stream().anyMatch(AND_WORDS::contains);
        boolean leftOr = left.stream().anyMatch(OR_WORDS::contains);
        boolean rightAnd = right.stream().anyMatch(AND_WORDS::contains);
        boolean rightOr = right.stream().anyMatch(OR_WORDS::contains);
        // Jedna ma tylko „i/oraz", druga tylko „lub/albo": inaczej to nie jest zamiana spójnika.
        boolean swapped = (leftAnd && !leftOr && rightOr && !rightAnd) || (rightAnd && !rightOr && leftOr && !leftAnd);
        if (!swapped) {
            return false;
        }
        if (mapConjunctions(left).equals(mapConjunctions(right))) {
            return true;
        }
        Set<String> leftWords = withoutConjunctions(left);
        Set<String> rightWords = withoutConjunctions(right);
        return jaccard(leftWords, rightWords) >= SIMILARITY;
    }

    private static List<String> mapConjunctions(List<String> tokens) {
        return tokens.stream()
                .map(t -> AND_WORDS.contains(t) || OR_WORDS.contains(t) ? CONJUNCTION_PLACEHOLDER : t)
                .toList();
    }

    private static Set<String> withoutConjunctions(List<String> tokens) {
        Set<String> words = new HashSet<>(tokens);
        words.removeAll(AND_WORDS);
        words.removeAll(OR_WORDS);
        return words;
    }

    private static double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() && right.isEmpty()) {
            return 0;
        }
        Set<String> common = new HashSet<>(left);
        common.retainAll(right);
        Set<String> all = new HashSet<>(left);
        all.addAll(right);
        return (double) common.size() / all.size();
    }
}
