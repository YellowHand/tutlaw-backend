package pl.tutlaw.backend.catalog;

import pl.tutlaw.backend.catalog.dto.TestCatalogResponse;
import pl.tutlaw.backend.catalog.dto.TestCatalogResponse.CategoryNode;
import pl.tutlaw.backend.catalog.dto.TestCatalogResponse.SourceNode;
import pl.tutlaw.backend.catalog.dto.TestCatalogResponse.SubjectNode;
import pl.tutlaw.backend.common.PolishSort;
import pl.tutlaw.backend.source.SourceType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Składa drzewo katalogu testów z płaskich list i zliczeń. Czysta logika bez bazy i Springa.
 */
final class CatalogAssembler {

    record CategoryRow(Long id, String name, String slug, String itemsLabel, int sortOrder) {
    }

    record SubjectRow(Long id, Long categoryId, String name, String slug) {
    }

    record SourceRow(Long id, Long subjectId, String name, SourceType sourceType) {
    }

    private CatalogAssembler() {
    }

    /**
     * @param totalBySource    wszystkie pytania testowe w źródle
     * @param readableBySource pytania dostępne teraz dla użytkownika
     * @param solvedBySource   pytania dostępne i rozwiązane co najmniej raz w teście
     */
    static TestCatalogResponse assemble(
            List<CategoryRow> categories,
            List<SubjectRow> subjects,
            List<SourceRow> sources,
            Map<Long, Long> totalBySource,
            Map<Long, Long> readableBySource,
            Map<Long, Long> solvedBySource
    ) {
        Map<Long, List<SourceRow>> sourcesBySubject = sources.stream()
                .collect(Collectors.groupingBy(SourceRow::subjectId));
        Map<Long, List<SubjectRow>> subjectsByCategory = subjects.stream()
                .collect(Collectors.groupingBy(SubjectRow::categoryId));

        List<CategoryNode> result = new ArrayList<>();
        for (CategoryRow category : categories.stream()
                .sorted(Comparator.comparingInt(CategoryRow::sortOrder)
                        .thenComparing(CategoryRow::name, PolishSort.byName()))
                .toList()) {

            List<SubjectNode> subjectNodes = new ArrayList<>();
            for (SubjectRow subject : subjectsByCategory.getOrDefault(category.id(), List.of()).stream()
                    .sorted(Comparator.comparing(SubjectRow::name, PolishSort.byName()))
                    .toList()) {

                List<SourceNode> sourceNodes = sourcesBySubject.getOrDefault(subject.id(), List.of()).stream()
                        .sorted(Comparator.comparing(SourceRow::id))
                        .map(source -> sourceNode(source, totalBySource, readableBySource, solvedBySource))
                        .toList();

                subjectNodes.add(new SubjectNode(
                        subject.id(), subject.name(), subject.slug(),
                        sum(sourceNodes, SourceNode::questionCount),
                        sum(sourceNodes, SourceNode::solvedCount),
                        sum(sourceNodes, SourceNode::lockedCount),
                        sourceNodes));
            }

            result.add(new CategoryNode(
                    category.id(), category.name(), category.slug(), category.itemsLabel(), category.sortOrder(),
                    sum(subjectNodes, SubjectNode::questionCount),
                    sum(subjectNodes, SubjectNode::solvedCount),
                    sum(subjectNodes, SubjectNode::lockedCount),
                    subjectNodes));
        }
        return new TestCatalogResponse(result);
    }

    private static SourceNode sourceNode(
            SourceRow source, Map<Long, Long> total, Map<Long, Long> readable, Map<Long, Long> solved) {
        int totalCount = total.getOrDefault(source.id(), 0L).intValue();
        int available = Math.min(readable.getOrDefault(source.id(), 0L).intValue(), totalCount);
        int solvedCount = Math.min(solved.getOrDefault(source.id(), 0L).intValue(), available);
        return new SourceNode(source.id(), source.name(), source.sourceType(), available, solvedCount, totalCount - available);
    }

    private static <T> int sum(List<T> nodes, java.util.function.ToIntFunction<T> value) {
        int total = 0;
        for (T node : nodes) {
            total += value.applyAsInt(node);
        }
        return total;
    }
}
