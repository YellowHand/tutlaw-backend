package pl.tutlaw.backend.mapping;

import org.junit.jupiter.api.Test;
import pl.tutlaw.backend.mapping.MappingSupport.Mapped;
import pl.tutlaw.backend.mapping.MappingSupport.SchemaColumn;
import pl.tutlaw.backend.mapping.MappingSupport.SchemaTable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Encje a schemat z migracji Flyway. Odtwarza to, co przy starcie sprawdza Hibernate ({@code ddl-auto: validate}):
 * czy tabela i kolumna istnieją i czy typ kolumny pasuje do typu pola. Do tego pilnuje rzeczy, których walidacja
 * nie widzi, a które psują dopiero zapis: pole enum dłuższe niż kolumna albo niezgodne z ograniczeniem CHECK,
 * kolumna NOT NULL bez wartości domyślnej, której encja nie wypełnia.
 */
class EntityMappingTest {

    private final Map<String, SchemaTable> schema = MappingSupport.schema();

    // ---------------------------------------------------------------- sam parser schematu

    @Test
    void parserReadsTheRealSchema_soTheOtherChecksCannotPassOnAnEmptyModel() {
        assertThat(schema).containsKeys("users", "questions", "user_question_states", "tracks", "track_acts", "legal_acts");
        assertThat(schema.get("tracks").columns().get("kind").allowedValues()).containsExactlyInAnyOrder("EXAM", "STUDENT");
        assertThat(schema.get("tracks").columns().get("slug").length()).isEqualTo(60);
        assertThat(schema.get("tracks").columns().get("id").identity()).isTrue();
        assertThat(schema.get("track_acts").columns().get("track_id").notNull()).isTrue();
    }

    @Test
    void parserHandlesChecksCompositeKeysAndComments() {
        Map<String, SchemaTable> tables = MappingSupport.parseSchema("""
                -- komentarz, (nawiasy) i, przecinki
                CREATE TABLE sample (
                    a BIGSERIAL PRIMARY KEY,
                    kind VARCHAR(10) NOT NULL DEFAULT 'ACT', -- drugi komentarz, z przecinkiem
                    flag CHAR(1),
                    amount DOUBLE PRECISION,
                    at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
                    CONSTRAINT chk_kind CHECK (kind IN ('ACT', 'TOPIC')),
                    CONSTRAINT pk UNIQUE (kind, flag)
                );
                CREATE TABLE pair (
                    x BIGINT NOT NULL,
                    y BIGINT NOT NULL,
                    PRIMARY KEY (x, y)
                );
                """);

        SchemaTable sample = tables.get("sample");
        assertThat(sample.columns()).containsOnlyKeys("a", "kind", "flag", "amount", "at");
        assertThat(sample.columns().get("kind").allowedValues()).containsExactly("ACT", "TOPIC");
        assertThat(sample.columns().get("kind").hasDefault()).isTrue();
        assertThat(sample.columns().get("flag").type()).isEqualTo("CHAR");
        assertThat(sample.columns().get("amount").type()).isEqualTo("DOUBLE");
        assertThat(sample.columns().get("at").type()).isEqualTo("TIMESTAMP");
        assertThat(tables.get("pair").columns()).containsOnlyKeys("x", "y");
    }

    @Test
    void parserAppliesAlterTableAddColumnAndReplacesCheckConstraints() {
        Map<String, SchemaTable> tables = MappingSupport.parseSchema("""
                CREATE TABLE sample (
                    id BIGSERIAL PRIMARY KEY,
                    kind VARCHAR(10) NOT NULL,
                    CONSTRAINT chk_kind CHECK (kind IN ('A', 'B'))
                );
                -- komentarz; ze średnikiem
                ALTER TABLE sample ADD COLUMN note VARCHAR(500);
                ALTER TABLE sample ADD COLUMN ids VARCHAR(500) NOT NULL DEFAULT '';
                ALTER TABLE sample ADD CONSTRAINT chk_note CHECK (note IS NULL OR length(note) > 0);
                ALTER TABLE sample DROP CONSTRAINT chk_kind;
                ALTER TABLE sample ADD CONSTRAINT chk_kind CHECK (kind IN ('A', 'B', 'C'));
                """);

        SchemaTable sample = tables.get("sample");
        assertThat(sample.columns()).containsOnlyKeys("id", "kind", "note", "ids");
        assertThat(sample.columns().get("note").notNull()).isFalse();
        assertThat(sample.columns().get("note").length()).isEqualTo(500);
        assertThat(sample.columns().get("ids").notNull()).isTrue();
        assertThat(sample.columns().get("ids").hasDefault()).isTrue();
        assertThat(sample.columns().get("kind").allowedValues()).containsExactly("A", "B", "C");
    }

    @Test
    void theRealSchemaIncludesTheColumnsAddedByLaterMigrations() {
        assertThat(schema.get("questions").columns()).containsKeys("law_changed_at", "law_change_note");
        assertThat(schema.get("custom_test_sessions").columns().get("kind").allowedValues())
                .containsExactlyInAnyOrder("ALL", "NEW", "MISTAKES", "DUE", "LAW_CHANGE", "TIME_LIMIT", "TRAPS");
        assertThat(schema).containsKey("user_courses");
    }

    @Test
    void theRealSchemaIncludesTheMarksAndExamAnswersAddedByTheLatestMigration() {
        assertThat(schema.get("question_marks").columns())
                .containsKeys("question_id", "mark", "origin", "status", "created_at", "reviewed_at", "reviewed_by");
        assertThat(schema.get("question_marks").columns().get("mark").allowedValues())
                .containsExactlyInAnyOrder("TIME_LIMIT", "TRAP_NEGATION", "TRAP_ABSOLUTE", "TRAP_NUMBERS",
                        "TRAP_CONJUNCTION", "TRAP_OUTDATED_LAW");
        assertThat(schema.get("question_marks").columns().get("status").allowedValues())
                .containsExactlyInAnyOrder("SUGGESTED", "CONFIRMED", "REJECTED");
        assertThat(schema.get("exam_attempts").columns()).containsKeys("excluded_count", "has_answers");
        assertThat(schema.get("exam_attempt_answers").columns())
                .containsKeys("attempt_id", "question_id", "position", "selected_option");
    }

    // ---------------------------------------------------------------- tabele i kolumny

    @Test
    void everyEntityHasItsTable() {
        List<String> problems = new ArrayList<>();
        for (Class<?> entity : MappingSupport.entities()) {
            if (!schema.containsKey(MappingSupport.tableName(entity))) {
                problems.add(entity.getSimpleName() + ": brak tabeli " + MappingSupport.tableName(entity));
            }
        }
        assertThat(problems).isEmpty();
        assertThat(MappingSupport.entities()).hasSizeGreaterThanOrEqualTo(20);
    }

    @Test
    void everyMappedFieldHasItsColumn() {
        List<String> problems = new ArrayList<>();
        for (Class<?> entity : MappingSupport.entities()) {
            SchemaTable table = schema.get(MappingSupport.tableName(entity));
            if (table == null) continue;
            for (Mapped mapped : MappingSupport.columnsOf(entity)) {
                if (!table.columns().containsKey(mapped.column())) {
                    problems.add(entity.getSimpleName() + "." + mapped.field().getName() + ": brak kolumny "
                            + table.name() + "." + mapped.column());
                }
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    void noTwoFieldsShareAColumn() {
        List<String> problems = new ArrayList<>();
        for (Class<?> entity : MappingSupport.entities()) {
            Set<String> seen = new HashSet<>();
            for (Mapped mapped : MappingSupport.columnsOf(entity)) {
                if (!seen.add(mapped.column())) {
                    problems.add(entity.getSimpleName() + ": kolumna " + mapped.column() + " jest zmapowana dwa razy");
                }
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    void joinTablesAndTheirColumnsExist() {
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (Class<?> entity : MappingSupport.entities()) {
            for (MappingSupport.JoinTableMapping join : MappingSupport.joinTablesOf(entity)) {
                checked++;
                SchemaTable table = schema.get(join.table());
                if (table == null) {
                    problems.add(entity.getSimpleName() + "." + join.field().getName() + ": brak tabeli łączącej " + join.table());
                    continue;
                }
                for (String column : join.columns()) {
                    if (!table.columns().containsKey(column)) {
                        problems.add(entity.getSimpleName() + "." + join.field().getName() + ": brak kolumny " + join.table() + "." + column);
                    }
                }
            }
        }
        assertThat(problems).isEmpty();
        assertThat(checked).isGreaterThanOrEqualTo(2); // ścieżka -> akty i przedmiot -> akty
    }

    // ---------------------------------------------------------------- typy

    @Test
    void columnTypesMatchWhatHibernateExpectsForTheFieldType() {
        List<String> problems = new ArrayList<>();
        for (Class<?> entity : MappingSupport.entities()) {
            SchemaTable table = schema.get(MappingSupport.tableName(entity));
            if (table == null) continue;
            for (Mapped mapped : MappingSupport.columnsOf(entity)) {
                SchemaColumn column = table.columns().get(mapped.column());
                if (column == null) continue;
                Set<String> accepted = acceptedTypes(mapped);
                if (!accepted.contains(column.type())) {
                    problems.add(entity.getSimpleName() + "." + mapped.field().getName() + " (" + describe(mapped) + ") ma kolumnę "
                            + table.name() + "." + column.name() + " typu " + column.type() + ", oczekiwano jednego z " + accepted);
                }
            }
        }
        assertThat(problems).isEmpty();
    }

    private static String describe(Mapped mapped) {
        return mapped.foreignKey() ? "klucz obcy" : mapped.javaType().getSimpleName() + (mapped.stringEnum() ? " @Enumerated(STRING)" : "");
    }

    private static Set<String> acceptedTypes(Mapped mapped) {
        if (mapped.foreignKey()) {
            return Set.of("BIGINT"); // wszystkie klucze główne w schemacie to BIGSERIAL
        }
        Class<?> type = mapped.javaType();
        if (type.isEnum()) {
            // enum zapisywany jako liczba (ORDINAL) jest w tym projekcie zabroniony: kolejność stałych nie może znaczyć danych
            return mapped.stringEnum() ? Set.of("VARCHAR", "TEXT") : Set.of();
        }
        if (type == Long.class || type == long.class) return Set.of("BIGINT");
        if (type == Integer.class || type == int.class) return Set.of("INTEGER");
        if (type == Short.class || type == short.class) return Set.of("SMALLINT");
        if (type == Double.class || type == double.class) return Set.of("DOUBLE");
        if (type == Float.class || type == float.class) return Set.of("REAL");
        if (type == Boolean.class || type == boolean.class) return Set.of("BOOLEAN");
        if (type == String.class) return Set.of("VARCHAR", "TEXT"); // CHAR(n) Hibernate odrzuca (to inny typ JDBC)
        if (type == LocalDateTime.class) return Set.of("TIMESTAMP");
        if (type == Instant.class) return Set.of("TIMESTAMPTZ", "TIMESTAMP");
        if (type == LocalDate.class) return Set.of("DATE");
        if (type == LocalTime.class) return Set.of("TIME");
        if (type == BigDecimal.class) return Set.of("NUMERIC");
        if (type == byte[].class) return Set.of("BYTEA");
        throw new AssertionError("Test mapowania nie zna typu " + type.getName() + " (pole " + mapped.field().getName()
                + "). Dopisz go w EntityMappingTest.acceptedTypes.");
    }

    @Test
    void enumColumnsFitEveryValueAndAgreeWithTheirCheckConstraint() {
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (Class<?> entity : MappingSupport.entities()) {
            SchemaTable table = schema.get(MappingSupport.tableName(entity));
            if (table == null) continue;
            for (Mapped mapped : MappingSupport.columnsOf(entity)) {
                if (!mapped.stringEnum()) continue;
                SchemaColumn column = table.columns().get(mapped.column());
                if (column == null) continue;
                checked++;
                List<String> names = new ArrayList<>(MappingSupport.enumNames(mapped.javaType()));
                String where = entity.getSimpleName() + "." + mapped.field().getName() + " -> " + table.name() + "." + column.name();
                if (column.length() != null) {
                    for (String name : names) {
                        if (name.length() > column.length()) {
                            problems.add(where + ": wartość " + name + " nie mieści się w VARCHAR(" + column.length() + ")");
                        }
                    }
                }
                if (column.allowedValues() != null) {
                    for (String name : names) {
                        if (!column.allowedValues().contains(name)) {
                            problems.add(where + ": wartość " + name + " nie jest dozwolona przez CHECK " + column.allowedValues());
                        }
                    }
                    for (String allowed : column.allowedValues()) {
                        if (!names.contains(allowed)) {
                            problems.add(where + ": CHECK dopuszcza " + allowed + ", której nie ma w enumie "
                                    + mapped.javaType().getSimpleName() + " (Hibernate nie odczyta takiego wiersza)");
                        }
                    }
                }
            }
        }
        assertThat(problems).isEmpty();
        assertThat(checked).isGreaterThanOrEqualTo(8);
    }

    // ---------------------------------------------------------------- zapis

    @Test
    void everyNotNullColumnWithoutADefaultIsFilledByTheEntity() {
        List<String> problems = new ArrayList<>();
        for (Class<?> entity : MappingSupport.entities()) {
            SchemaTable table = schema.get(MappingSupport.tableName(entity));
            if (table == null) continue;
            Set<String> mapped = new HashSet<>();
            for (Mapped m : MappingSupport.columnsOf(entity)) mapped.add(m.column());
            for (SchemaColumn column : table.columns().values()) {
                if (column.notNull() && !column.hasDefault() && !mapped.contains(column.name())) {
                    problems.add(entity.getSimpleName() + ": kolumna " + table.name() + "." + column.name()
                            + " jest NOT NULL bez DEFAULT, a encja jej nie ustawia (każdy zapis się wywali)");
                }
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    void generatedIdsLiveInSerialColumns() {
        List<String> problems = new ArrayList<>();
        for (Class<?> entity : MappingSupport.entities()) {
            SchemaTable table = schema.get(MappingSupport.tableName(entity));
            if (table == null) continue;
            for (Mapped mapped : MappingSupport.columnsOf(entity)) {
                if (!mapped.generated()) continue;
                SchemaColumn column = table.columns().get(mapped.column());
                if (column != null && !column.identity()) {
                    problems.add(entity.getSimpleName() + "." + mapped.field().getName()
                            + " ma @GeneratedValue, a kolumna " + table.name() + "." + column.name() + " nie jest BIGSERIAL/IDENTITY");
                }
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    void everyEntityHasAnId() {
        List<String> problems = new ArrayList<>();
        for (Class<?> entity : MappingSupport.entities()) {
            if (MappingSupport.columnsOf(entity).stream().noneMatch(Mapped::id)) {
                problems.add(entity.getSimpleName() + ": brak pola @Id");
            }
        }
        assertThat(problems).isEmpty();
    }
}
