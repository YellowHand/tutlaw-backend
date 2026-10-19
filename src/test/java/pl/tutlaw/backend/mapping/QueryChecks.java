package pl.tutlaw.backend.mapping;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Statyczne sprawdzenia zapytań repozytoriów, które inaczej wychodzą dopiero przy starcie Springa: sklejone słowa
 * kluczowe, parametry bez {@code @Param}, nieistniejące pola encji w JPQL, aliasy projekcji i nazwy właściwości
 * w metodach wyprowadzanych z nazwy. Każda metoda zwraca listę problemów (pustą, gdy wszystko w porządku), dzięki czemu
 * da się sprawdzić także, że sprawdzenie naprawdę łapie błędy.
 */
final class QueryChecks {

    private QueryChecks() {
    }

    // ------------------------------------------------------------------------------------------ tekst zapytania

    /** Zapytanie bez literałów tekstowych ('...'), żeby dwukropki i kropki w tekście nie były brane za parametry i ścieżki. */
    static String withoutLiterals(String query) {
        return query.replaceAll("'(?:[^']|'')*'", "''");
    }

    private static final Pattern GLUED_AFTER_KEYWORD = Pattern.compile("\\b(WHERE|AND|FROM|JOIN|BY|ON|SET|HAVING)(?=[a-z_])");
    private static final Pattern GLUED_BEFORE_KEYWORD = Pattern.compile(
            "[a-z0-9_)](?=(?:WHERE|AND|GROUP BY|ORDER BY|HAVING|JOIN|LEFT JOIN)\\b)");

    /** Sklejone fragmenty (np. {@code WHEREq.id} albo {@code :ids)GROUP BY}): skutek konkatenacji bez spacji. */
    static List<String> glue(String label, String query) {
        List<String> problems = new ArrayList<>();
        String q = withoutLiterals(query);
        Matcher after = GLUED_AFTER_KEYWORD.matcher(q);
        while (after.find()) {
            problems.add(label + ": słowo kluczowe " + after.group(1) + " sklejone z następnym wyrazem: ..." + around(q, after.start()) + "...");
        }
        Matcher before = GLUED_BEFORE_KEYWORD.matcher(q);
        while (before.find()) {
            problems.add(label + ": słowo kluczowe sklejone z poprzednim wyrazem: ..." + around(q, before.start()) + "...");
        }
        int open = (int) q.chars().filter(c -> c == '(').count();
        int close = (int) q.chars().filter(c -> c == ')').count();
        if (open != close) {
            problems.add(label + ": nawiasy się nie zgadzają (" + open + " otwierających, " + close + " zamykających)");
        }
        if (q.isBlank()) {
            problems.add(label + ": puste zapytanie");
        }
        return problems;
    }

    private static String around(String text, int index) {
        return text.substring(Math.max(0, index - 15), Math.min(text.length(), index + 25)).replace('\n', ' ');
    }

    // ------------------------------------------------------------------------------------------ parametry

    private static final Pattern NAMED_PARAM = Pattern.compile("(?<![:\\w]):([A-Za-z_]\\w*)");

    /** Nazwy {@code :param} użyte w zapytaniu i nazwy z {@code @Param} muszą się zgadzać w obie strony. */
    static List<String> params(String label, Method method, String query) {
        List<String> problems = new ArrayList<>();
        String q = withoutLiterals(query);
        if (q.matches("(?s).*\\?\\d+.*")) {
            return problems; // parametry pozycyjne: nie sprawdzamy nazw
        }
        Set<String> used = new LinkedHashSet<>();
        Matcher m = NAMED_PARAM.matcher(q);
        while (m.find()) used.add(m.group(1));

        Set<String> declared = new LinkedHashSet<>();
        for (Parameter p : method.getParameters()) {
            String type = p.getType().getSimpleName();
            if (type.equals("Pageable") || type.equals("Sort")) continue;
            Param param = p.getAnnotation(Param.class);
            if (param == null) {
                problems.add(label + ": parametr " + type + " bez @Param");
            } else if (!declared.add(param.value())) {
                problems.add(label + ": @Param(\"" + param.value() + "\") użyty dwa razy");
            }
        }
        for (String name : used) {
            if (!declared.contains(name)) problems.add(label + ": zapytanie używa :" + name + ", a metoda nie ma @Param(\"" + name + "\")");
        }
        for (String name : declared) {
            if (!used.contains(name)) problems.add(label + ": @Param(\"" + name + "\") nie występuje w zapytaniu (Spring odrzuci taką metodę)");
        }
        return problems;
    }

    // ------------------------------------------------------------------------------------------ ścieżki JPQL

    private static final Set<String> RESERVED = Set.of("WHERE", "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "FULL", "CROSS", "GROUP",
            "ORDER", "HAVING", "ON", "SET", "FETCH", "UNION", "AS", "WITH", "SELECT", "INTO", "VALUES", "AND", "OR", "NOT", "IN",
            "USING", "LIMIT", "OFFSET", "FROM");

    /** Alias to dowolny wyraz poza słowami zastrzeżonymi (inaczej „JOIN" po encji zostałby wzięty za alias i zjadłby kolejne złączenie). */
    private static final String ALIAS = "(?:\\s+(?:AS\\s+)?(?!(?:" + String.join("|", RESERVED) + ")\\b)([A-Za-z_]\\w*))?";
    private static final Pattern FROM_ITEM = Pattern.compile("(?i)\\b(?:FROM|UPDATE)\\s+([A-Za-z_][\\w.]*)" + ALIAS);
    private static final Pattern FROM_COMMA = Pattern.compile("(?i)\\G\\s*,\\s*([A-Za-z_][\\w.]*)" + ALIAS);
    private static final Pattern JOIN_ITEM = Pattern.compile("(?i)\\bJOIN\\s+(?:FETCH\\s+)?([A-Za-z_][\\w.]*)" + ALIAS);
    private static final Pattern PATH = Pattern.compile("(?<![\\w.:])([A-Za-z_]\\w*)\\.([A-Za-z_]\\w*(?:\\.[A-Za-z_]\\w*)*)");

    /** Encje w FROM/JOIN istnieją, a każda ścieżka {@code alias.pole.pole} prowadzi do istniejących pól encji. */
    static List<String> paths(String label, String query, Map<String, Class<?>> entitiesByName) {
        List<String> problems = new ArrayList<>();
        String q = withoutLiterals(query);
        Map<String, List<Class<?>>> aliases = new HashMap<>();

        Matcher from = FROM_ITEM.matcher(q);
        while (from.find()) {
            addFromItem(label, from.group(1), from.group(2), entitiesByName, aliases, problems);
            int end = from.end();
            Matcher comma = FROM_COMMA.matcher(q);
            comma.region(end, q.length());
            while (comma.lookingAt()) {
                addFromItem(label, comma.group(1), comma.group(2), entitiesByName, aliases, problems);
                comma.region(comma.end(), q.length());
            }
        }

        List<String[]> pendingJoins = new ArrayList<>();
        Matcher join = JOIN_ITEM.matcher(q);
        while (join.find()) {
            String target = join.group(1);
            String alias = join.group(2) == null || RESERVED.contains(join.group(2).toUpperCase()) ? null : join.group(2);
            if (!target.contains(".")) {
                Class<?> entity = entitiesByName.get(target);
                if (entity == null) {
                    problems.add(label + ": JOIN z nieznaną encją " + target);
                } else if (alias != null) {
                    aliases.computeIfAbsent(alias, k -> new ArrayList<>()).add(entity);
                }
            } else {
                pendingJoins.add(new String[]{target, alias});
            }
        }
        // złączenia po relacji zależą od aliasów wcześniejszych złączeń: rozwiązujemy w kilku przebiegach
        boolean progress = true;
        List<String[]> remaining = new ArrayList<>(pendingJoins);
        while (progress && !remaining.isEmpty()) {
            progress = false;
            for (String[] pending : new ArrayList<>(remaining)) {
                String[] segments = pending[0].split("\\.");
                List<Class<?>> starts = aliases.get(segments[0]);
                if (starts == null) continue;
                List<Class<?>> resolved = resolve(starts, segments, 1);
                remaining.remove(pending);
                progress = true;
                if (resolved.isEmpty()) {
                    problems.add(label + ": JOIN po nieistniejącej relacji " + pending[0]);
                } else if (pending[1] != null) {
                    aliases.computeIfAbsent(pending[1], k -> new ArrayList<>()).addAll(resolved);
                }
            }
        }
        for (String[] pending : remaining) {
            problems.add(label + ": JOIN " + pending[0] + " zaczyna się od nieznanego aliasu");
        }

        Matcher path = PATH.matcher(q);
        while (path.find()) {
            List<Class<?>> starts = aliases.get(path.group(1));
            if (starts == null) continue; // pełna nazwa klasy, np. w SELECT NEW, albo wywołanie funkcji
            String[] segments = (path.group(1) + "." + path.group(2)).split("\\.");
            if (resolve(starts, segments, 1).isEmpty()) {
                problems.add(label + ": nie ma pola " + path.group(2) + " w encji "
                        + starts.stream().map(Class::getSimpleName).distinct().toList() + " (ścieżka " + path.group(0) + ")");
            }
        }
        return problems;
    }

    private static void addFromItem(String label, String entityName, String alias, Map<String, Class<?>> entities,
                                    Map<String, List<Class<?>>> aliases, List<String> problems) {
        if (entityName.contains(".")) {
            return; // EXTRACT(x FROM a.b) albo nazwa kwalifikowana: to nie jest encja
        }
        Class<?> entity = entities.get(entityName);
        if (entity == null) {
            problems.add(label + ": nieznana encja w FROM/UPDATE: " + entityName);
            return;
        }
        if (alias != null && !RESERVED.contains(alias.toUpperCase())) {
            aliases.computeIfAbsent(alias, k -> new ArrayList<>()).add(entity);
        }
    }

    /** Idzie po polach od {@code from} i zwraca klasy, w których kończy się ścieżka (pusta lista: ścieżka nie istnieje). */
    private static List<Class<?>> resolve(List<Class<?>> starts, String[] segments, int from) {
        List<Class<?>> ends = new ArrayList<>();
        for (Class<?> start : starts) {
            Class<?> current = start;
            boolean ok = true;
            for (int i = from; i < segments.length && ok; i++) {
                Field field = MappingSupport.findField(current, segments[i]);
                if (field == null) {
                    ok = false;
                } else if (i < segments.length - 1) {
                    if (MappingSupport.isToOne(field) || MappingSupport.isToMany(field)) {
                        current = MappingSupport.relationTarget(field);
                    } else {
                        ok = false; // pole podstawowe nie ma pól wewnątrz
                    }
                } else {
                    current = MappingSupport.isToOne(field) || MappingSupport.isToMany(field)
                            ? MappingSupport.relationTarget(field) : field.getType();
                }
            }
            if (ok) ends.add(current);
        }
        return ends;
    }

    // ------------------------------------------------------------------------------------------ projekcje

    private static final Pattern SELECT_ALIAS = Pattern.compile("(?i)\\bAS\\s+([A-Za-z_]\\w*)");

    /** Metoda zwracająca interfejs-projekcję musi mieć w SELECT alias dla każdego gettera. */
    static List<String> projection(String label, Method method, String query) {
        List<String> problems = new ArrayList<>();
        if (method.isDefault()) return problems;
        Class<?> element = elementType(method.getGenericReturnType());
        if (element == null || !element.isInterface() || element.isAnnotationPresent(jakarta.persistence.Entity.class)
                || element.getName().startsWith("java.")) {
            return problems;
        }
        if (Pattern.compile("(?i)SELECT\\s+(?:DISTINCT\\s+)?NEW\\s").matcher(query).find()) {
            return problems;
        }
        Set<String> aliases = new LinkedHashSet<>();
        Matcher m = SELECT_ALIAS.matcher(withoutLiterals(query));
        while (m.find()) aliases.add(m.group(1));
        for (Method getter : element.getMethods()) {
            if (getter.getParameterCount() != 0 || getter.isDefault() || getter.getDeclaringClass() == Object.class) continue;
            String name = getter.getName();
            String property;
            if (name.startsWith("get") && name.length() > 3) property = name.substring(3);
            else if (name.startsWith("is") && name.length() > 2) property = name.substring(2);
            else continue;
            property = Character.toLowerCase(property.charAt(0)) + property.substring(1);
            if (!aliases.contains(property)) {
                problems.add(label + ": projekcja " + element.getSimpleName() + " wymaga aliasu \"" + property
                        + "\" (getter " + name + "), a zapytanie ma tylko " + aliases);
            }
        }
        return problems;
    }

    /** Typ elementu wyniku: List&lt;X&gt;, Optional&lt;X&gt;, Set&lt;X&gt;, Page&lt;X&gt; albo samo X. */
    static Class<?> elementType(Type returnType) {
        if (returnType instanceof ParameterizedType p && p.getActualTypeArguments().length == 1
                && p.getActualTypeArguments()[0] instanceof Class<?> c) {
            return c;
        }
        return returnType instanceof Class<?> c ? c : null;
    }

    // ------------------------------------------------------------------------------------------ metody z nazwy

    private static final Pattern DERIVED = Pattern.compile("^(?:find|read|get|query|search|stream|count|exists|delete|remove)\\w*?By([A-Z]\\w*)$");
    private static final List<String> SUFFIXES = List.of("IgnoreCase", "GreaterThanEqual", "LessThanEqual", "IsNotNull", "IsNotEmpty",
            "StartingWith", "EndingWith", "Containing", "GreaterThan", "LessThan", "IsEmpty", "IsNull", "NotNull", "NotEmpty", "NotLike",
            "NotIn", "IsTrue", "IsFalse", "Between", "Before", "After", "Exists", "Equals", "Like", "True", "False", "Null", "Not", "In", "Is");

    /** Właściwości użyte w nazwie metody ({@code findByUserIdAndQuestionId}) istnieją w encji. */
    static List<String> derived(String label, Class<?> entity, Method method) {
        List<String> problems = new ArrayList<>();
        Matcher m = DERIVED.matcher(method.getName());
        if (!m.matches()) return problems;
        String[] halves = m.group(1).split("OrderBy", 2);
        if (!halves[0].isEmpty()) {
            for (String part : halves[0].split("(?<=[a-z0-9])(?:And|Or)(?=[A-Z])")) {
                if (!propertyExists(entity, stripSuffix(entity, part))) {
                    problems.add(label + ": w nazwie metody jest właściwość \"" + part + "\", której nie ma w encji " + entity.getSimpleName());
                }
            }
        }
        if (halves.length > 1) {
            for (String part : halves[1].split("(?<=Asc|Desc)(?=[A-Z])")) {
                String property = part.replaceAll("(Asc|Desc)$", "");
                if (!property.isEmpty() && !propertyExists(entity, property)) {
                    problems.add(label + ": w sortowaniu z nazwy metody jest \"" + property + "\", której nie ma w encji " + entity.getSimpleName());
                }
            }
        }
        return problems;
    }

    private static String stripSuffix(Class<?> entity, String part) {
        String current = part;
        for (int round = 0; round < 3; round++) {
            if (propertyExists(entity, current)) return current;
            boolean stripped = false;
            for (String suffix : SUFFIXES) {
                if (current.endsWith(suffix) && current.length() > suffix.length()) {
                    current = current.substring(0, current.length() - suffix.length());
                    stripped = true;
                    break;
                }
            }
            if (!stripped) break;
        }
        return current;
    }

    /** {@code UserId} to pole {@code userId} albo {@code user.id}: próbujemy każdego podziału na pole i resztę ścieżki. */
    static boolean propertyExists(Class<?> entity, String expression) {
        for (int i = 1; i <= expression.length(); i++) {
            if (i < expression.length() && !Character.isUpperCase(expression.charAt(i))) continue;
            String head = Character.toLowerCase(expression.charAt(0)) + expression.substring(1, i);
            Field field = MappingSupport.findField(entity, head);
            if (field == null) continue;
            if (i == expression.length()) return true;
            if (MappingSupport.isToOne(field) || MappingSupport.isToMany(field)) {
                if (propertyExists(MappingSupport.relationTarget(field), expression.substring(i))) return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------ całość

    /** Wszystkie sprawdzenia dla jednej metody repozytorium z {@code @Query}. */
    static List<String> allForQueryMethod(Class<?> repository, Method method, Map<String, Class<?>> entities) {
        Query query = method.getAnnotation(Query.class);
        String label = repository.getSimpleName() + "." + method.getName();
        List<String> problems = new ArrayList<>(glue(label, query.value()));
        problems.addAll(params(label, method, query.value()));
        if (!query.nativeQuery()) {
            problems.addAll(paths(label, query.value(), entities));
            problems.addAll(projection(label, method, query.value()));
        }
        return problems;
    }
}
