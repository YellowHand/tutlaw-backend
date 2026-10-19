package pl.tutlaw.backend.pool;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tutlaw.backend.common.GroupCount;
import pl.tutlaw.backend.common.PolishSort;
import pl.tutlaw.backend.legal.LegalAct;
import pl.tutlaw.backend.legal.LegalActRepository;
import pl.tutlaw.backend.legal.LegalField;
import pl.tutlaw.backend.legal.LegalFieldRepository;
import pl.tutlaw.backend.pool.dto.PoolResponse;
import pl.tutlaw.backend.pool.dto.UpdatePoolActsRequest;
import pl.tutlaw.backend.pool.dto.UpdateSourceFilterRequest;
import pl.tutlaw.backend.question.QuestionRef;
import pl.tutlaw.backend.question.QuestionRepository;
import pl.tutlaw.backend.settings.UserSettings;
import pl.tutlaw.backend.settings.UserSettingsRepository;
import pl.tutlaw.backend.source.QuestionSourceRepository;
import pl.tutlaw.backend.track.Track;
import pl.tutlaw.backend.track.TrackRepository;
import pl.tutlaw.backend.track.dto.TrackSummary;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Okno „Pula pytań": odczyt puli użytkownika oraz dwie rzeczy, które sam ustawia: zawężenie do wybranych aktów
 * prawnych (trwałe, nie dotyczy egzaminu próbnego) i filtr źródeł w Testach (własne arkusze, arkusze innych ścieżek,
 * pytania autorskie). Samą regułę puli liczy {@link PoolService}.
 */
@Service
@RequiredArgsConstructor
public class PoolSettingsService {

    private final PoolService poolService;
    private final UserSettingsRepository settingsRepository;
    private final UserPoolActRepository poolActRepository;
    private final TrackRepository trackRepository;
    private final LegalFieldRepository fieldRepository;
    private final LegalActRepository actRepository;
    private final QuestionRepository questionRepository;
    private final QuestionSourceRepository sourceRepository;
    private final CurrentUserResolver currentUserResolver;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PoolResponse get(Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        return describe(user.getId());
    }

    /**
     * Zawęża pulę do podanych aktów. Akty spoza ścieżki użytkownika są odrzucane (błąd), a zaznaczenie wszystkich
     * aktów ścieżki albo pusta lista zdejmuje zawężenie, żeby akty dodane później przez administratora były widoczne.
     */
    @Transactional
    public PoolResponse updateActs(UpdatePoolActsRequest request, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        Pool pool = poolService.forUser(user.getId());
        Set<Long> base = new HashSet<>(pool.baseActIds());

        Set<Long> chosen = request.actIds().stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        for (Long id : chosen) {
            if (!base.contains(id)) {
                throw new IllegalArgumentException("Wybrany akt prawny nie należy do Twojej puli pytań.");
            }
        }

        poolActRepository.deleteByUserId(user.getId());
        poolActRepository.flush();
        if (!chosen.isEmpty() && !chosen.containsAll(base)) {
            poolActRepository.saveAll(chosen.stream().map(id -> new UserPoolAct(user.getId(), id)).toList());
        }
        return describe(user.getId());
    }

    /** Zapisuje filtr źródeł. Co najmniej jedna grupa musi zostać włączona: filtr nie może ukryć całego materiału. */
    @Transactional
    public PoolResponse updateSourceFilter(UpdateSourceFilterRequest request, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        SourceFilter filter = new SourceFilter(request.ownSheets(), request.otherSheets(), request.authored());
        if (filter.isNone()) {
            throw new IllegalArgumentException("Zaznacz co najmniej jedną grupę pytań.");
        }

        UserSettings settings = settingsRepository.findById(user.getId()).orElseGet(() -> new UserSettings(user.getId()));
        settings.setIncludeOwnSheets(filter.ownSheets());
        settings.setIncludeOtherSheets(filter.otherSheets());
        settings.setIncludeAuthored(filter.authored());
        settings.setUpdatedAt(LocalDateTime.now(clock));
        settingsRepository.save(settings);
        return describe(user.getId());
    }

    private PoolResponse describe(Long userId) {
        Pool pool = poolService.forUser(userId);
        Track track = pool.trackId() == null ? null : trackRepository.findById(pool.trackId()).orElse(null);

        // Akty ścieżki (bez zawężenia) z liczbą pytań ze wszystkich źródeł, do zaznaczania.
        Map<Long, Long> perAct = new HashMap<>();
        for (GroupCount row : questionRepository.countByAct(pool.baseActIds(), pool.allSourceIds())) {
            perAct.put(row.getGroupId(), row.getTotal());
        }
        Set<Long> selected = new HashSet<>(pool.actIds());
        Map<Long, List<LegalAct>> actsByField = actRepository.findAllWithFieldByIdIn(pool.baseActIds()).stream()
                .collect(Collectors.groupingBy(a -> a.getLegalField().getId()));

        List<PoolResponse.FieldNode> fields = new ArrayList<>();
        for (LegalField field : fieldRepository.findAll().stream()
                .sorted(Comparator.comparingInt(LegalField::getSortOrder).thenComparing(LegalField::getName, PolishSort.byName()))
                .toList()) {
            List<PoolResponse.ActNode> acts = actsByField.getOrDefault(field.getId(), List.of()).stream()
                    .sorted(Comparator.comparingInt(LegalAct::getSortOrder).thenComparing(LegalAct::getName, PolishSort.byName()))
                    .map(act -> new PoolResponse.ActNode(
                            act.getId(), act.getName(), act.getShortName(), act.getKind(),
                            perAct.getOrDefault(act.getId(), 0L).intValue(), selected.contains(act.getId())))
                    .toList();
            fields.add(new PoolResponse.FieldNode(field.getId(), field.getName(), field.getSlug(), acts));
        }

        List<QuestionRef> refs = questionRepository.findRefsInPool(pool.actIds(), pool.allSourceIds());
        Map<Long, String> groupBySource = groupsBySource(pool);
        Map<String, Integer> perGroup = new HashMap<>();
        int inPool = 0;
        Set<Long> allowed = new HashSet<>(pool.sourceIds());
        for (QuestionRef ref : refs) {
            perGroup.merge(groupBySource.getOrDefault(ref.getSourceId(), SourceFilter.OTHER_SHEETS), 1, Integer::sum);
            if (allowed.contains(ref.getSourceId())) {
                inPool++;
            }
        }
        SourceFilter filter = pool.filter();
        List<PoolResponse.SourceGroupNode> groups = List.of(
                new PoolResponse.SourceGroupNode(SourceFilter.OWN_SHEETS, filter.ownSheets(), perGroup.getOrDefault(SourceFilter.OWN_SHEETS, 0)),
                new PoolResponse.SourceGroupNode(SourceFilter.OTHER_SHEETS, filter.otherSheets(), perGroup.getOrDefault(SourceFilter.OTHER_SHEETS, 0)),
                new PoolResponse.SourceGroupNode(SourceFilter.AUTHORED, filter.authored(), perGroup.getOrDefault(SourceFilter.AUTHORED, 0)));

        return new PoolResponse(
                track == null ? null : TrackSummary.of(track), pool.narrowed(), inPool, fields, groups);
    }

    /** Do której z trzech grup filtra należy każde źródło. */
    private Map<Long, String> groupsBySource(Pool pool) {
        Set<Long> own = new HashSet<>(pool.ownSheetIds());
        Map<Long, String> result = new HashMap<>();
        for (QuestionSourceRepository.SourceRow source : sourceRepository.findAllRows()) {
            result.put(source.getId(), SourceGroups.of(source.getKind(), source.getId(), own));
        }
        return result;
    }
}
