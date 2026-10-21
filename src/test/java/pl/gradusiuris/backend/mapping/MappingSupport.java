package pl.gradusiuris.backend.mapping;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Wspólne narzędzia testów pilnujących zgodności kodu ze schematem bazy: wyszukiwanie encji i repozytoriów,
 * czytanie schematu z migracji Flyway (V*.sql) i opis mapowania pól encji na kolumny.
 *
 * <p>Po co: Hibernate ({@code ddl-auto: validate}) i Spring Data sprawdzają to dopiero przy starcie aplikacji,
 * a literówka w nazwie kolumny, pola albo parametru zapytania kosztuje wtedy cały start. Te testy łapią to wcześniej,
 * bez bazy i bez Springa.
 */
final class MappingSupport {

    static final String ROOT_PACKAGE = "pl.gradusiuris.backend";

    private MappingSupport() {
    }

    // ------------------------------------------------------------------------------------------ klasy aplikacji

    private static List<Class<?>> cachedClasses;

    /** Wszystkie klasy kodu produkcyjnego (katalog {@code target/classes} albo jar), bez ich inicjowania. */
    static synchronized List<Class<?>> applicationClasses() {
        if (cachedClasses != null) {
            return cachedClasses;
        }
        List<String> names = new ArrayList<>();
        try {
            Path location = Path.of(pl.gradusiuris.backend.user.User.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isDirectory(location)) {
                try (Stream<Path> files = Files.walk(location)) {
                    files.filter(p -> p.toString().endsWith(".class")).forEach(p -> names.add(className(location.relativize(p).toString())));
                }
            } else {
                try (JarFile jar = new JarFile(location.toFile())) {
                    jar.stream().map(JarEntry::getName).filter(n -> n.endsWith(".class"))
                            .filter(n -> n.contains("pl/gradusiuris/backend/")).forEach(n -> names.add(className(n)));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        List<Class<?>> classes = new ArrayList<>();
        for (String name : names) {
            if (!name.startsWith(ROOT_PACKAGE) || name.endsWith("package-info") || name.endsWith("module-info")) {
                continue;
            }
            try {
                classes.add(Class.forName(name, false, MappingSupport.class.getClassLoader()));
            } catch (ClassNotFoundException | LinkageError e) {
                throw new IllegalStateException("Nie da się wczytać klasy " + name, e);
            }
        }
        classes.sort(Comparator.comparing(Class::getName));
        cachedClasses = classes;
        return classes;
    }

    private static String className(String relativePath) {
        return relativePath.replace('\\', '/').replace('/', '.').replaceAll("\\.class$", "");
    }

    static List<Class<?>> entities() {
        return applicationClasses().stream().filter(c -> c.isAnnotationPresent(Entity.class)).toList();
    }

    static List<Class<?>> repositories() {
        return applicationClasses().stream()
                .filter(c -> c.isInterface() && JpaRepository.class.isAssignableFrom(c) && c != JpaRepository.class)
                .toList();
    }

    /** Encja, którą obsługuje repozytorium ({@code JpaRepository<Encja, Id>}). */
    static Class<?> entityOf(Class<?> repository) {
        for (Type type : repository.getGenericInterfaces()) {
            if (type instanceof ParameterizedType p && p.getRawType() == JpaRepository.class
                    && p.getActualTypeArguments()[0] instanceof Class<?> entity) {
                return entity;
            }
        }
        throw new IllegalStateException("Repozytorium " + repository.getName() + " nie rozszerza JpaRepository<Encja, Id>");
    }

    /** Nazwa encji w zapytaniach JPQL: {@code @Entity(name)} albo nazwa prostej klasy. */
    static String jpqlName(Class<?> entity) {
        String name = entity.getAnnotation(Entity.class).name();
        return name.isEmpty() ? entity.getSimpleName() : name;
    }

    static Map<String, Class<?>> entitiesByJpqlName() {
        Map<String, Class<?>> map = new LinkedHashMap<>();
        for (Class<?> entity : entities()) {
            map.put(jpqlName(entity), entity);
        }
        return map;
    }

    // ------------------------------------------------------------------------------------------ mapowanie pól

    /** Odpowiednik strategii nazw Spring Boota (camelCase na snake_case; podkreślnik tylko wewnątrz słowa). */
    static String snake(String name) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char current = name.charAt(i);
            if (i > 0 && i < name.length() - 1) {
                char before = name.charAt(i - 1);
                char after = name.charAt(i + 1);
                if (Character.isLowerCase(before) && Character.isUpperCase(current) && Character.isLowerCase(after)) {
                    out.append('_');
                }
            }
            out.append(Character.toLowerCase(current));
        }
        return out.toString();
    }

    static String tableName(Class<?> entity) {
        Table table = entity.getAnnotation(Table.class);
        return table == null || table.name().isEmpty() ? snake(entity.getSimpleName()) : table.name();
    }

    /** Pola trwałe encji razem z polami klas bazowych. */
    static List<Field> persistentFields(Class<?> entity) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = entity; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || Modifier.isTransient(f.getModifiers())
                        || f.isAnnotationPresent(Transient.class) || f.isSynthetic()) {
                    continue;
                }
                fields.add(f);
            }
        }
        return fields;
    }

    static boolean has(Field field, String annotationSimpleName) {
        for (Annotation a : field.getAnnotations()) {
            if (a.annotationType().getSimpleName().equals(annotationSimpleName)) {
                return true;
            }
        }
        return false;
    }

    static boolean isToOne(Field f) {
        return has(f, "ManyToOne");
    }

    static boolean isToMany(Field f) {
        return has(f, "ManyToMany") || has(f, "OneToMany");
    }

    /** Pole encji o danej nazwie (z klas bazowych) albo {@code null}. */
    static Field findField(Class<?> entity, String name) {
        for (Field f : persistentFields(entity)) {
            if (f.getName().equals(name)) {
                return f;
            }
        }
        return null;
    }

    /** Typ, na który wskazuje relacja: pole encji albo element kolekcji. */
    static Class<?> relationTarget(Field f) {
        if (f.getGenericType() instanceof ParameterizedType p && p.getActualTypeArguments()[0] instanceof Class<?> c) {
            return c;
        }
        return f.getType();
    }

    /** Opis kolumny pola podstawowego albo klucza obcego. */
    record Mapped(Class<?> entity, Field field, String column, boolean id, boolean generated, boolean foreignKey) {
        Class<?> javaType() {
            return field.getType();
        }

        boolean stringEnum() {
            Enumerated enumerated = field.getAnnotation(Enumerated.class);
            return field.getType().isEnum() && enumerated != null && enumerated.value() == EnumType.STRING;
        }
    }

    /** Jedna para (tabela łącząca, kolumna) wymagana przez relację wiele-do-wielu. */
    record JoinTableMapping(Class<?> entity, Field field, String table, List<String> columns) {
    }

    private static final Set<String> UNSUPPORTED = Set.of("OneToOne", "Embedded", "EmbeddedId", "ElementCollection", "Convert", "Lob");

    static List<Mapped> columnsOf(Class<?> entity) {
        List<Mapped> mapped = new ArrayList<>();
        for (Field f : persistentFields(entity)) {
            for (String unsupported : UNSUPPORTED) {
                if (has(f, unsupported)) {
                    throw new AssertionError(entity.getSimpleName() + "." + f.getName() + " używa @" + unsupported
                            + ", którego nie obsługuje test mapowania. Dopisz obsługę w MappingSupport.");
                }
            }
            if (isToMany(f)) {
                continue;
            }
            boolean id = has(f, "Id");
            boolean generated = has(f, "GeneratedValue");
            if (isToOne(f)) {
                JoinColumn join = f.getAnnotation(JoinColumn.class);
                String column = join != null && !join.name().isEmpty() ? join.name() : snake(f.getName()) + "_id";
                mapped.add(new Mapped(entity, f, column, id, generated, true));
            } else {
                Column col = f.getAnnotation(Column.class);
                String column = col != null && !col.name().isEmpty() ? col.name() : snake(f.getName());
                mapped.add(new Mapped(entity, f, column, id, generated, false));
            }
        }
        return mapped;
    }

    static List<JoinTableMapping> joinTablesOf(Class<?> entity) {
        List<JoinTableMapping> result = new ArrayList<>();
        for (Field f : persistentFields(entity)) {
            if (has(f, "ManyToMany") && f.isAnnotationPresent(JoinTable.class)) {
                JoinTable table = f.getAnnotation(JoinTable.class);
                List<String> columns = new ArrayList<>();
                for (JoinColumn c : table.joinColumns()) columns.add(c.name());
                for (JoinColumn c : table.inverseJoinColumns()) columns.add(c.name());
                result.add(new JoinTableMapping(entity, f, table.name(), columns));
            }
        }
        return result;
    }

    // ------------------------------------------------------------------------------------------ schemat z migracji

    /** Kolumna ze schematu. {@code type} to rodzaj znormalizowany: BIGINT, INTEGER, VARCHAR, TEXT, TIMESTAMP... */
    record SchemaColumn(String name, String type, Integer length, boolean notNull, boolean hasDefault,
                        boolean identity, Set<String> allowedValues) {
    }

    record SchemaTable(String name, Map<String, SchemaColumn> columns) {
    }

    private static final Path MIGRATIONS = Path.of("src", "main", "resources", "db", "migration");

    /** Schemat zbudowany z migracji w kolejności numerów. Pomija test, gdy katalog z migracjami jest poza zasięgiem. */
    static Map<String, SchemaTable> schema() {
        assumeTrue(Files.isDirectory(MIGRATIONS), "Brak katalogu migracji " + MIGRATIONS + " (test uruchomiony poza katalogiem modułu)");
        List<Path> files;
        try (Stream<Path> list = Files.list(MIGRATIONS)) {
            files = list.filter(p -> p.getFileName().toString().matches("V\\d+__.*\\.sql"))
                    .sorted(Comparator.comparingInt(p -> Integer.parseInt(p.getFileName().toString().replaceAll("^V(\\d+)__.*$", "$1"))))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        StringBuilder sql = new StringBuilder();
        for (Path file : files) {
            try {
                sql.append(Files.readString(file, StandardCharsets.UTF_8)).append('\n');
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return parseSchema(sql.toString());
    }

    private static final Pattern CREATE_TABLE = Pattern.compile("(?i)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(\\w+)\\s*\\(");
    private static final Pattern COLUMN_TYPE = Pattern.compile(
            "(?i)^(BIGSERIAL|SERIAL|SMALLSERIAL|BIGINT|INTEGER|INT|SMALLINT|DOUBLE\\s+PRECISION|FLOAT8|REAL|NUMERIC(?:\\(\\s*\\d+\\s*(?:,\\s*\\d+\\s*)?\\))?"
                    + "|DECIMAL|BOOLEAN|BOOL|VARCHAR(?:\\(\\s*(\\d+)\\s*\\))?|CHARACTER\\s+VARYING(?:\\(\\s*(\\d+)\\s*\\))?"
                    + "|CHAR(?:\\(\\s*(\\d+)\\s*\\))?|CHARACTER(?:\\(\\s*(\\d+)\\s*\\))?|BPCHAR|TEXT|TIMESTAMPTZ"
                    + "|TIMESTAMP(?:\\s+WITH(?:OUT)?\\s+TIME\\s+ZONE)?|DATE|TIME|BYTEA|UUID|JSONB?)(?!\\w)");
    private static final Set<String> TABLE_CONSTRAINTS = Set.of("CONSTRAINT", "PRIMARY", "UNIQUE", "FOREIGN", "CHECK", "EXCLUDE");

    private static final Pattern ALTER_ADD_COLUMN = Pattern.compile("(?is)ALTER\\s+TABLE\\s+(\\w+)\\s+ADD\\s+COLUMN\\s+([^;]+);");
    private static final Pattern ALTER_ADD_CHECK_IN = Pattern.compile(
            "(?is)ALTER\\s+TABLE\\s+(\\w+)\\s+ADD\\s+CONSTRAINT\\s+\\w+\\s+CHECK\\s*\\(\\s*(\\w+)\\s+IN\\s*\\(([^)]*)\\)\\s*\\)");

    /**
     * Czyta CREATE TABLE z tekstu SQL, a potem kolejne ALTER TABLE ... ADD COLUMN i ADD CONSTRAINT ... CHECK (kolumna IN (...))
     * (nowe ograniczenie zastępuje poprzednie dla tej kolumny). Rozpoznaje tylko to, czego potrzebują testy (kolumny, typy,
     * NOT NULL, DEFAULT, CHECK ... IN).
     */
    static Map<String, SchemaTable> parseSchema(String sql) {
        String clean = Pattern.compile("--[^\\n]*").matcher(sql).replaceAll("");
        Map<String, SchemaTable> tables = new LinkedHashMap<>();
        Matcher m = CREATE_TABLE.matcher(clean);
        while (m.find()) {
            int start = m.end();
            int depth = 1;
            int i = start;
            boolean quoted = false;
            while (i < clean.length() && depth > 0) {
                char c = clean.charAt(i);
                if (c == '\'') quoted = !quoted;
                else if (!quoted && c == '(') depth++;
                else if (!quoted && c == ')') depth--;
                i++;
            }
            String body = clean.substring(start, i - 1);
            tables.put(m.group(1).toLowerCase(), new SchemaTable(m.group(1).toLowerCase(), parseColumns(body)));
        }
        Matcher add = ALTER_ADD_COLUMN.matcher(clean);
        while (add.find()) {
            tableOf(tables, add.group(1)).columns().putAll(parseColumns(add.group(2)));
        }
        Matcher check = ALTER_ADD_CHECK_IN.matcher(clean);
        while (check.find()) {
            Map<String, SchemaColumn> columns = tableOf(tables, check.group(1)).columns();
            String name = check.group(2).toLowerCase();
            SchemaColumn column = columns.get(name);
            if (column == null) {
                throw new IllegalStateException("CHECK na nieznanej kolumnie: " + check.group(1) + "." + name);
            }
            Set<String> values = new LinkedHashSet<>();
            Matcher v = Pattern.compile("'([^']*)'").matcher(check.group(3));
            while (v.find()) values.add(v.group(1));
            columns.put(name, new SchemaColumn(column.name(), column.type(), column.length(), column.notNull(),
                    column.hasDefault(), column.identity(), values));
        }
        return tables;
    }

    private static SchemaTable tableOf(Map<String, SchemaTable> tables, String name) {
        SchemaTable table = tables.get(name.toLowerCase());
        if (table == null) {
            throw new IllegalStateException("ALTER TABLE na nieznanej tabeli: " + name);
        }
        return table;
    }

    private static Map<String, SchemaColumn> parseColumns(String body) {
        List<String> pieces = new ArrayList<>();
        int depth = 0;
        boolean quoted = false;
        StringBuilder current = new StringBuilder();
        for (char c : body.toCharArray()) {
            if (c == '\'') quoted = !quoted;
            if (!quoted && c == '(') depth++;
            if (!quoted && c == ')') depth--;
            if (!quoted && depth == 0 && c == ',') {
                pieces.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (!current.toString().isBlank()) {
            pieces.add(current.toString().trim());
        }

        // Ograniczenia CHECK (kolumna IN (...)) zebrane z całej tabeli, także z ograniczeń na poziomie tabeli.
        Map<String, Set<String>> allowed = new LinkedHashMap<>();
        Matcher check = Pattern.compile("(?is)CHECK\\s*\\(\\s*(\\w+)\\s+IN\\s*\\(([^)]*)\\)\\s*\\)").matcher(body);
        while (check.find()) {
            Set<String> values = new LinkedHashSet<>();
            Matcher v = Pattern.compile("'([^']*)'").matcher(check.group(2));
            while (v.find()) values.add(v.group(1));
            allowed.put(check.group(1).toLowerCase(), values);
        }
        Set<String> primaryKey = new LinkedHashSet<>();
        Matcher pk = Pattern.compile("(?is)PRIMARY\\s+KEY\\s*\\(([^)]*)\\)").matcher(body);
        while (pk.find()) {
            for (String column : pk.group(1).split(",")) primaryKey.add(column.trim().toLowerCase());
        }

        Map<String, SchemaColumn> columns = new LinkedHashMap<>();
        for (String piece : pieces) {
            if (piece.isEmpty()) continue;
            String[] words = piece.split("\\s+", 2);
            if (TABLE_CONSTRAINTS.contains(words[0].toUpperCase())) continue;
            String name = words[0].replace("\"", "").toLowerCase();
            String rest = words.length > 1 ? words[1].trim() : "";
            Matcher t = COLUMN_TYPE.matcher(rest);
            if (!t.find()) {
                throw new IllegalStateException("Nie rozpoznaję typu kolumny: " + piece);
            }
            String raw = t.group(1).toUpperCase().replaceAll("\\s+", " ");
            String after = rest.substring(t.end()).toUpperCase();
            Integer length = null;
            for (int g = 2; g <= 5; g++) {
                if (t.group(g) != null) length = Integer.parseInt(t.group(g));
            }
            boolean identity = raw.endsWith("SERIAL") || after.contains("GENERATED");
            String type = normalizeType(raw);
            boolean notNull = after.contains("NOT NULL") || after.contains("PRIMARY KEY") || primaryKey.contains(name) || identity;
            boolean hasDefault = after.contains("DEFAULT") || identity;
            columns.put(name, new SchemaColumn(name, type, length, notNull, hasDefault, identity, allowed.get(name)));
        }
        return columns;
    }

    private static String normalizeType(String raw) {
        if (raw.startsWith("BIGSERIAL") || raw.startsWith("BIGINT")) return "BIGINT";
        if (raw.startsWith("SMALLSERIAL") || raw.startsWith("SMALLINT")) return "SMALLINT";
        if (raw.startsWith("SERIAL") || raw.startsWith("INTEGER") || raw.equals("INT")) return "INTEGER";
        if (raw.startsWith("DOUBLE") || raw.equals("FLOAT8")) return "DOUBLE";
        if (raw.equals("REAL")) return "REAL";
        if (raw.startsWith("NUMERIC") || raw.startsWith("DECIMAL")) return "NUMERIC";
        if (raw.startsWith("BOOL")) return "BOOLEAN";
        if (raw.startsWith("VARCHAR") || raw.startsWith("CHARACTER VARYING")) return "VARCHAR";
        if (raw.startsWith("CHAR") || raw.equals("BPCHAR")) return "CHAR";
        if (raw.equals("TEXT")) return "TEXT";
        if (raw.equals("TIMESTAMPTZ") || raw.contains("WITH TIME ZONE")) return "TIMESTAMPTZ";
        if (raw.startsWith("TIMESTAMP")) return "TIMESTAMP";
        if (raw.startsWith("JSON")) return "JSON";
        return raw;
    }

    static Collection<String> enumNames(Class<?> enumType) {
        List<String> names = new ArrayList<>();
        for (Object constant : enumType.getEnumConstants()) {
            names.add(((Enum<?>) constant).name());
        }
        return names;
    }
}
