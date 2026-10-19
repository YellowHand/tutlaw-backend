package pl.tutlaw.backend.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tutlaw.backend.catalog.CatalogAssembler.ActRow;
import pl.tutlaw.backend.catalog.CatalogAssembler.FieldRow;
import pl.tutlaw.backend.catalog.CatalogAssembler.SourceRow;
import pl.tutlaw.backend.catalog.dto.TestCatalogResponse;
import pl.tutlaw.backend.common.GroupCount;
import pl.tutlaw.backend.legal.LegalAct;
import pl.tutlaw.backend.legal.LegalActRepository;
import pl.tutlaw.backend.legal.LegalFieldRepository;
import pl.tutlaw.backend.pool.Pool;
import pl.tutlaw.backend.pool.PoolService;
import pl.tutlaw.backend.question.QuestionRepository;
import pl.tutlaw.backend.review.ReviewLogRepository;
import pl.tutlaw.backend.source.QuestionSourceRepository;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TestCatalogService {

    private final LegalFieldRepository fieldRepository;
    private final LegalActRepository actRepository;
    private final QuestionSourceRepository sourceRepository;
    private final QuestionRepository questionRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final CurrentUserResolver currentUserResolver;
    private final PoolService poolService;

    /**
     * Całe drzewo Testów jednym wywołaniem. Tabele dziedzin, aktów i źródeł są małe, a liczniki pytań pochodzą
     * z czterech zapytań grupujących, więc koszt nie rośnie z liczbą pytań. Liczą się tylko pytania z puli użytkownika
     * (akty jego ścieżki i zawężenie) i z wybranych przez niego źródeł (filtr źródeł).
     */
    @Transactional(readOnly = true)
    public TestCatalogResponse getCatalog(Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        Pool pool = poolService.forUser(user.getId());

        List<FieldRow> fields = fieldRepository.findAll().stream()
                .map(f -> new FieldRow(f.getId(), f.getName(), f.getSlug(), f.getSortOrder()))
                .toList();
        List<ActRow> acts = actRepository.findAllWithFieldByIdIn(pool.actIds()).stream()
                .map(TestCatalogService::toRow)
                .toList();

        Set<Long> allowedSources = new HashSet<>(pool.sourceIds());
        List<SourceRow> sources = sourceRepository.findAllRows().stream()
                .filter(s -> allowedSources.contains(s.getId()))
                .map(s -> new SourceRow(s.getId(), s.getName(), s.getKind(), s.getExamYear()))
                .toList();

        return CatalogAssembler.assemble(
                pool.narrowed(), fields, acts, sources, new HashSet<>(pool.ownSheetIds()),
                toMap(questionRepository.countByAct(pool.actIds(), pool.sourceIds())),
                toMap(reviewLogRepository.countSolvedTestQuestionsByAct(user.getId(), pool.actIds(), pool.sourceIds())),
                toMap(questionRepository.countBySource(pool.actIds(), pool.sourceIds())),
                toMap(reviewLogRepository.countSolvedTestQuestionsBySource(user.getId(), pool.actIds(), pool.sourceIds())));
    }

    static ActRow toRow(LegalAct act) {
        return new ActRow(act.getId(), act.getLegalField().getId(), act.getName(), act.getShortName(),
                act.getSlug(), act.getKind(), act.getSortOrder());
    }

    static Map<Long, Long> toMap(List<GroupCount> rows) {
        Map<Long, Long> result = new HashMap<>();
        for (GroupCount row : rows) {
            result.put(row.getGroupId(), row.getTotal());
        }
        return result;
    }
}
