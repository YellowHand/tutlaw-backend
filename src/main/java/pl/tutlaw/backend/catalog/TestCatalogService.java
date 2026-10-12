package pl.tutlaw.backend.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import pl.tutlaw.backend.card.CardRepository;
import pl.tutlaw.backend.catalog.CatalogAssembler.CategoryRow;
import pl.tutlaw.backend.catalog.CatalogAssembler.SourceRow;
import pl.tutlaw.backend.catalog.CatalogAssembler.SubjectRow;
import pl.tutlaw.backend.catalog.dto.TestCatalogResponse;
import pl.tutlaw.backend.category.CategoryRepository;
import pl.tutlaw.backend.common.GroupCount;
import pl.tutlaw.backend.review.ReviewLogRepository;
import pl.tutlaw.backend.source.SourceRepository;
import pl.tutlaw.backend.subject.SubjectRepository;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TestCatalogService {

    private final CategoryRepository categoryRepository;
    private final SubjectRepository subjectRepository;
    private final SourceRepository sourceRepository;
    private final CardRepository cardRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final CurrentUserResolver currentUserResolver;

    /**
     * Całe drzewo Testów jednym wywołaniem. Tabele kategorii, przedmiotów i źródeł są małe,
     * a liczniki pytań pochodzą z trzech zapytań grupujących, więc koszt nie rośnie z liczbą kart.
     */
    public TestCatalogResponse getCatalog(Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);

        List<CategoryRow> categories = categoryRepository.findAll().stream()
                .map(c -> new CategoryRow(c.getId(), c.getName(), c.getSlug(), c.getItemsLabel(), c.getSortOrder()))
                .toList();
        List<SubjectRow> subjects = subjectRepository.findAll().stream()
                .map(s -> new SubjectRow(s.getId(), s.getCategory().getId(), s.getName(), s.getSlug()))
                .toList();
        List<SourceRow> sources = sourceRepository.findAll().stream()
                .map(s -> new SourceRow(s.getId(), s.getSubject().getId(), s.getName(), s.getSourceType()))
                .toList();

        return CatalogAssembler.assemble(
                categories, subjects, sources,
                toMap(cardRepository.countTestQuestionsBySource()),
                toMap(cardRepository.countReadableTestQuestionsBySource(user.getId())),
                toMap(reviewLogRepository.countSolvedTestQuestionsBySource(user.getId())));
    }

    private static Map<Long, Long> toMap(List<GroupCount> rows) {
        Map<Long, Long> result = new HashMap<>();
        for (GroupCount row : rows) {
            result.put(row.getGroupId(), row.getTotal());
        }
        return result;
    }
}
