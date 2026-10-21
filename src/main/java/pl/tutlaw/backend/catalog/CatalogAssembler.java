package pl.tutlaw.backend.catalog;

import pl.tutlaw.backend.catalog.dto.TestCatalogResponse;
import pl.tutlaw.backend.catalog.dto.TestCatalogResponse.ActNode;
import pl.tutlaw.backend.catalog.dto.TestCatalogResponse.CourseNode;
import pl.tutlaw.backend.catalog.dto.TestCatalogResponse.FieldNode;
import pl.tutlaw.backend.catalog.dto.TestCatalogResponse.SectionNode;
import pl.tutlaw.backend.common.PolishSort;
import pl.tutlaw.backend.legal.LegalActKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * Składa drzewo katalogu testów z płaskich list i zliczeń. Czysta logika bez bazy i Springa.
 */
final class CatalogAssembler {

    record FieldRow(Long id, String name, String slug, int sortOrder) {
    }

    record ActRow(Long id, Long fieldId, String name, String shortName, String slug, LegalActKind kind, int sortOrder) {
    }

    record SectionRow(Long id, Long actId, String name, int sortOrder) {
    }

    private CatalogAssembler() {
    }

    /**
     * @param fields         wszystkie dziedziny (te bez aktów w puli są pomijane)
     * @param actsInPool     tylko akty z puli użytkownika
     * @param sections       rozdziały aktów z puli
     * @param totalByAct     pytania w akcie (z wybranego źródła)
     * @param solvedByAct    z nich rozwiązane co najmniej raz w teście
     * @param totalBySection pytania w rozdziale (z wybranego źródła); rozdział bez pytań jest pomijany
     */
    static TestCatalogResponse assemble(
            boolean narrowed,
            List<FieldRow> fields,
            List<ActRow> actsInPool,
            List<SectionRow> sections,
            Map<Long, Long> totalByAct,
            Map<Long, Long> solvedByAct,
            Map<Long, Long> totalBySection
    ) {
        Map<Long, List<ActRow>> actsByField = actsInPool.stream().collect(Collectors.groupingBy(ActRow::fieldId));
        Map<Long, List<SectionRow>> sectionsByAct = sections.stream().collect(Collectors.groupingBy(SectionRow::actId));

        List<FieldNode> fieldNodes = new ArrayList<>();
        for (FieldRow field : fields.stream()
                .sorted(Comparator.comparingInt(FieldRow::sortOrder).thenComparing(FieldRow::name, PolishSort.byName()))
                .toList()) {
            List<ActNode> actNodes = actsByField.getOrDefault(field.id(), List.of()).stream()
                    .sorted(Comparator.comparingInt(ActRow::sortOrder).thenComparing(ActRow::name, PolishSort.byName()))
                    .map(act -> actNode(act, totalByAct, solvedByAct, sectionsByAct.getOrDefault(act.id(), List.of()), totalBySection))
                    .toList();
            if (actNodes.isEmpty()) {
                continue; // dziedzina bez aktów w puli niczego nie daje użytkownikowi
            }
            fieldNodes.add(new FieldNode(
                    field.id(), field.name(), field.slug(),
                    sum(actNodes, ActNode::questionCount), sum(actNodes, ActNode::solvedCount), actNodes));
        }
        return new TestCatalogResponse(narrowed, fieldNodes, List.of());
    }

    /** Przedmiot z aktami do liczenia pytań. */
    record CourseRow(Long id, String name, String slug, int sortOrder, List<Long> actIds) {
    }

    /**
     * Przedmioty studenta z licznikami z ich aktów. Przedmioty bez żadnego pytania są pomijane. Akty mogą należeć do
     * kilku przedmiotów, więc przedmioty liczone są osobno (suma przedmiotów może przekraczać liczbę pytań w puli).
     */
    static List<CourseNode> courseNodes(List<CourseRow> courses, Map<Long, Long> totalByAct, Map<Long, Long> solvedByAct) {
        List<CourseNode> nodes = new ArrayList<>();
        for (CourseRow course : courses.stream()
                .sorted(Comparator.comparingInt(CourseRow::sortOrder).thenComparing(CourseRow::name, PolishSort.byName())
                        .thenComparing(CourseRow::id))
                .toList()) {
            int total = 0;
            int solved = 0;
            for (Long actId : course.actIds()) {
                int actTotal = totalByAct.getOrDefault(actId, 0L).intValue();
                total += actTotal;
                solved += Math.min(solvedByAct.getOrDefault(actId, 0L).intValue(), actTotal);
            }
            if (total == 0) {
                continue;
            }
            nodes.add(new CourseNode(course.id(), course.name(), course.slug(), total, solved, List.copyOf(course.actIds())));
        }
        return nodes;
    }

    private static ActNode actNode(
            ActRow act, Map<Long, Long> total, Map<Long, Long> solved,
            List<SectionRow> sections, Map<Long, Long> totalBySection) {
        int totalCount = total.getOrDefault(act.id(), 0L).intValue();
        int solvedCount = Math.min(solved.getOrDefault(act.id(), 0L).intValue(), totalCount);
        List<SectionNode> sectionNodes = sections.stream()
                .sorted(Comparator.comparingInt(SectionRow::sortOrder)
                        .thenComparing(SectionRow::name, PolishSort.byName())
                        .thenComparing(SectionRow::id))
                .map(section -> new SectionNode(
                        section.id(), section.name(), totalBySection.getOrDefault(section.id(), 0L).intValue()))
                .filter(section -> section.questionCount() > 0) // rozdział bez pytań (w tym źródle) nic nie daje
                .toList();
        return new ActNode(act.id(), act.name(), act.shortName(), act.slug(), act.kind(), totalCount, solvedCount, sectionNodes);
    }

    private static <T> int sum(List<T> nodes, ToIntFunction<T> value) {
        int total = 0;
        for (T node : nodes) {
            total += value.applyAsInt(node);
        }
        return total;
    }
}
