package pl.tutlaw.backend.catalog;

import pl.tutlaw.backend.catalog.dto.TestCatalogResponse;
import pl.tutlaw.backend.catalog.dto.TestCatalogResponse.ActNode;
import pl.tutlaw.backend.catalog.dto.TestCatalogResponse.FieldNode;
import pl.tutlaw.backend.catalog.dto.TestCatalogResponse.SourceNode;
import pl.tutlaw.backend.common.PolishSort;
import pl.tutlaw.backend.legal.LegalActKind;
import pl.tutlaw.backend.pool.SourceFilter;
import pl.tutlaw.backend.pool.SourceGroups;
import pl.tutlaw.backend.source.SourceKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    record SourceRow(Long id, String name, SourceKind kind, Integer examYear) {
    }

    private CatalogAssembler() {
    }

    /**
     * @param fields         wszystkie dziedziny
     * @param actsInPool     tylko akty z puli użytkownika
     * @param sources        źródła dozwolone filtrem źródeł
     * @param ownSheetIds    arkusze wybranej ścieżki
     * @param totalByAct     pytania w akcie (z dozwolonych źródeł)
     * @param solvedByAct    z nich rozwiązane co najmniej raz w teście
     * @param totalBySource  pytania w źródle (z aktów puli)
     * @param solvedBySource z nich rozwiązane co najmniej raz w teście
     */
    static TestCatalogResponse assemble(
            boolean narrowed,
            List<FieldRow> fields,
            List<ActRow> actsInPool,
            List<SourceRow> sources,
            Set<Long> ownSheetIds,
            Map<Long, Long> totalByAct,
            Map<Long, Long> solvedByAct,
            Map<Long, Long> totalBySource,
            Map<Long, Long> solvedBySource
    ) {
        Map<Long, List<ActRow>> actsByField = actsInPool.stream().collect(Collectors.groupingBy(ActRow::fieldId));

        List<FieldNode> fieldNodes = new ArrayList<>();
        for (FieldRow field : fields.stream()
                .sorted(Comparator.comparingInt(FieldRow::sortOrder).thenComparing(FieldRow::name, PolishSort.byName()))
                .toList()) {
            List<ActNode> actNodes = actsByField.getOrDefault(field.id(), List.of()).stream()
                    .sorted(Comparator.comparingInt(ActRow::sortOrder).thenComparing(ActRow::name, PolishSort.byName()))
                    .map(act -> actNode(act, totalByAct, solvedByAct))
                    .toList();
            fieldNodes.add(new FieldNode(
                    field.id(), field.name(), field.slug(),
                    sum(actNodes, ActNode::questionCount), sum(actNodes, ActNode::solvedCount), actNodes));
        }

        List<SourceNode> sourceNodes = new ArrayList<>();
        for (SourceRow source : sources.stream().sorted(sourceOrder(ownSheetIds)).toList()) {
            int total = totalBySource.getOrDefault(source.id(), 0L).intValue();
            if (total == 0) {
                continue; // źródło bez pytań w puli użytkownika niczego mu nie daje
            }
            int solved = Math.min(solvedBySource.getOrDefault(source.id(), 0L).intValue(), total);
            sourceNodes.add(new SourceNode(
                    source.id(), source.name(), source.kind(), groupOf(source, ownSheetIds), source.examYear(), total, solved));
        }
        return new TestCatalogResponse(narrowed, fieldNodes, sourceNodes);
    }

    /** Grupa filtra źródeł, do której należy źródło. */
    static String groupOf(SourceRow source, Set<Long> ownSheetIds) {
        return SourceGroups.of(source.kind(), source.id(), ownSheetIds);
    }

    /** Własne arkusze, potem arkusze innych ścieżek, na końcu pytania autorskie; w grupie od najnowszego roku. */
    private static Comparator<SourceRow> sourceOrder(Set<Long> ownSheetIds) {
        return Comparator
                .comparingInt((SourceRow s) -> rank(groupOf(s, ownSheetIds)))
                .thenComparing((SourceRow s) -> s.examYear() == null ? 0 : -s.examYear())
                .thenComparing(SourceRow::name, PolishSort.byName())
                .thenComparing(SourceRow::id);
    }

    private static int rank(String group) {
        return switch (group) {
            case SourceFilter.OWN_SHEETS -> 0;
            case SourceFilter.OTHER_SHEETS -> 1;
            default -> 2;
        };
    }

    private static ActNode actNode(ActRow act, Map<Long, Long> total, Map<Long, Long> solved) {
        int totalCount = total.getOrDefault(act.id(), 0L).intValue();
        int solvedCount = Math.min(solved.getOrDefault(act.id(), 0L).intValue(), totalCount);
        return new ActNode(act.id(), act.name(), act.shortName(), act.slug(), act.kind(), totalCount, solvedCount);
    }

    private static <T> int sum(List<T> nodes, ToIntFunction<T> value) {
        int total = 0;
        for (T node : nodes) {
            total += value.applyAsInt(node);
        }
        return total;
    }
}
