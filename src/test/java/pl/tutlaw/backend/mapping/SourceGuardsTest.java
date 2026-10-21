package pl.tutlaw.backend.mapping;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Reguły, które da się sprawdzić tylko w tekście źródeł (adnotacje, które po kompilacji niczego nie zdradzają). */
class SourceGuardsTest {

    private static final Path MAIN = Path.of("src", "main", "java");

    /**
     * Pole {@code @Enumerated(STRING)} razem z {@code @Column(length = 1)}: kolumna na jeden znak to w PostgreSQL
     * {@code char(1)}, a Hibernate waliduje je jako inny typ niż tekst i odmawia startu.
     */
    private static final Pattern ENUM_STRING_WITH_ONE_CHAR_COLUMN = Pattern.compile(
            "@Enumerated\\(\\s*(?:EnumType\\.)?STRING\\s*\\)\\s*@Column\\([^)]*\\blength\\s*=\\s*1\\b[^)]*\\)");

    @Test
    void noStringEnumIsMappedToAOneCharacterColumn() {
        assumeTrue(Files.isDirectory(MAIN), "Test uruchomiony poza katalogiem modułu");
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                if (ENUM_STRING_WITH_ONE_CHAR_COLUMN.matcher(text).find()) {
                    problems.add(file.toString());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertThat(problems).isEmpty();
    }

    @Test
    void theOneCharacterGuardRecognisesTheForbiddenShapes() {
        assertThat(ENUM_STRING_WITH_ONE_CHAR_COLUMN.matcher(
                "@Enumerated(EnumType.STRING)\n    @Column(name = \"x\", length = 1)").find()).isTrue();
        assertThat(ENUM_STRING_WITH_ONE_CHAR_COLUMN.matcher(
                "@Enumerated(STRING) @Column(length=1, nullable = false)").find()).isTrue();
        assertThat(ENUM_STRING_WITH_ONE_CHAR_COLUMN.matcher(
                "@Enumerated(EnumType.STRING)\n    @Column(nullable = false, length = 10)").find()).isFalse();
        assertThat(ENUM_STRING_WITH_ONE_CHAR_COLUMN.matcher(
                "@Enumerated(EnumType.STRING)\n    @Column(length = 12)").find()).isFalse();
    }

    /** Kontrolery pod {@code /api/admin} (panel i narzędzia administratora) i ich zabezpieczenie. */
    private static final Pattern ADMIN_MAPPING = Pattern.compile("Mapping\\(\\s*(?:value\\s*=\\s*)?\"/api/admin");

    @Test
    void everyAdminControllerRequiresTheAdminRole_andTheSecurityConfigGuardsThePathToo() {
        assumeTrue(Files.isDirectory(MAIN), "Test uruchomiony poza katalogiem modułu");
        List<String> problems = new ArrayList<>();
        int admin = 0;
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(p -> p.toString().endsWith("Controller.java")).toList()) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                if (ADMIN_MAPPING.matcher(text).find()) {
                    admin++;
                    if (!text.contains("@PreAuthorize(\"hasRole('ADMIN')\")")) {
                        problems.add(file + ": endpoint pod /api/admin bez @PreAuthorize(\"hasRole('ADMIN')\")");
                    }
                }
            }
            Path config = MAIN.resolve(Path.of("pl", "tutlaw", "backend", "common", "config", "SecurityConfig.java"));
            String security = Files.readString(config, StandardCharsets.UTF_8);
            if (!security.contains("\"/api/admin/**\").hasRole(\"ADMIN\")")) {
                problems.add("SecurityConfig nie wymaga roli ADMIN dla /api/admin/**");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertThat(problems).isEmpty();
        assertThat(admin).isGreaterThanOrEqualTo(1);
    }

    @Test
    void theAdminGuardRecognisesAdminMappings() {
        assertThat(ADMIN_MAPPING.matcher("@RequestMapping(\"/api/admin/question-marks\")").find()).isTrue();
        assertThat(ADMIN_MAPPING.matcher("@GetMapping(value = \"/api/admin/x\")").find()).isTrue();
        assertThat(ADMIN_MAPPING.matcher("@RequestMapping(\"/api/exams\")").find()).isFalse();
    }

    @Test
    void noEnumIsStoredByOrdinal() {
        assumeTrue(Files.isDirectory(MAIN), "Test uruchomiony poza katalogiem modułu");
        // kolejność stałych enuma nie może znaczyć danych: każde pole enum w encji ma @Enumerated(STRING)
        List<String> problems = new ArrayList<>();
        for (Class<?> entity : MappingSupport.entities()) {
            for (var mapped : MappingSupport.columnsOf(entity)) {
                if (mapped.javaType().isEnum() && !mapped.stringEnum()) {
                    problems.add(entity.getSimpleName() + "." + mapped.field().getName());
                }
            }
        }
        assertThat(problems).isEmpty();
    }
}
