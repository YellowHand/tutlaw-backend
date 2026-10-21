package pl.tutlaw.backend.pool;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tutlaw.backend.common.GroupCount;
import pl.tutlaw.backend.common.PolishSort;
import pl.tutlaw.backend.course.Course;
import pl.tutlaw.backend.course.CourseRepository;
import pl.tutlaw.backend.legal.LegalAct;
import pl.tutlaw.backend.legal.LegalActRepository;
import pl.tutlaw.backend.legal.LegalField;
import pl.tutlaw.backend.legal.LegalFieldRepository;
import pl.tutlaw.backend.pool.dto.PoolResponse;
import pl.tutlaw.backend.pool.dto.UpdatePoolActsRequest;
import pl.tutlaw.backend.pool.dto.UpdatePoolCoursesRequest;
import pl.tutlaw.backend.question.QuestionRepository;
import pl.tutlaw.backend.track.Track;
import pl.tutlaw.backend.track.TrackRepository;
import pl.tutlaw.backend.track.dto.TrackSummary;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

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
 * Okno „Pula pytań": odczyt puli użytkownika i jedna rzecz, którą sam ustawia: zawężenie do wybranych aktów
 * prawnych (student: do wybranych przedmiotów; trwałe, nie dotyczy egzaminu próbnego). Źródło pytań (Ministerstwo,
 * autorskie) wybiera się osobno przy każdym teście. Samą regułę puli liczy {@link PoolService}.
 */
@Service
@RequiredArgsConstructor
public class PoolSettingsService {

    private final PoolService poolService;
    private final UserPoolActRepository poolActRepository;
    private final UserCourseRepository userCourseRepository;
    private final CourseRepository courseRepository;
    private final TrackRepository trackRepository;
    private final LegalFieldRepository fieldRepository;
    private final LegalActRepository actRepository;
    private final QuestionRepository questionRepository;
    private final CurrentUserResolver currentUserResolver;

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

    /**
     * Student wybiera przedmioty, z których się uczy. Wybór ma sens tylko na ścieżce Student, a numery muszą wskazywać
     * przedmioty, które mają akty. Zaznaczenie wszystkich przedmiotów albo pusta lista zdejmuje wybór, żeby
     * przedmioty dodane później przez administratora były widoczne. Postęp w nauce nie zmienia się: odpowiedzi
     * należą do pytań, nie do przedmiotów.
     */
    @Transactional
    public PoolResponse updateCourses(UpdatePoolCoursesRequest request, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        Pool pool = poolService.forUser(user.getId());
        if (!pool.isStudent()) {
            throw new IllegalArgumentException("Przedmioty wybierasz na ścieżce Student.");
        }
        StudentCourses courses = poolService.studentCourses(user.getId());

        Set<Long> chosen = request.courseIds().stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        for (Long id : chosen) {
            if (!courses.actsByCourse().containsKey(id)) {
                throw new IllegalArgumentException("Wybrany przedmiot nie istnieje albo nie ma jeszcze pytań.");
            }
        }

        userCourseRepository.deleteByUserId(user.getId());
        userCourseRepository.flush();
        if (!chosen.isEmpty() && !chosen.containsAll(courses.actsByCourse().keySet())) {
            userCourseRepository.saveAll(chosen.stream().map(id -> new UserCourse(user.getId(), id)).toList());
        }
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
            if (acts.isEmpty()) {
                continue; // dziedzina bez aktów w puli niczego nie daje (np. „Student" użytkownikowi aplikacji)
            }
            fields.add(new PoolResponse.FieldNode(field.getId(), field.getName(), field.getSlug(), acts));
        }

        int inPool = questionRepository.findRefsInPool(pool.actIds(), pool.allSourceIds()).size();

        return new PoolResponse(
                track == null ? null : TrackSummary.of(track), pool.narrowed(), inPool, fields,
                pool.isStudent() ? describeCourses(userId, pool) : List.of());
    }

    /** Przedmioty do wyboru: tylko takie, w których są jakieś pytania, w kolejności ustalonej przez administratora. */
    private List<PoolResponse.CourseNode> describeCourses(Long userId, Pool pool) {
        StudentCourses courses = poolService.studentCourses(userId);
        Set<Long> allActs = courses.actsByCourse().values().stream().flatMap(List::stream).collect(Collectors.toSet());
        if (allActs.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> perAct = new HashMap<>();
        for (GroupCount row : questionRepository.countByAct(allActs, pool.allSourceIds())) {
            perAct.put(row.getGroupId(), row.getTotal());
        }
        List<PoolResponse.CourseNode> nodes = new ArrayList<>();
        for (Course course : courseRepository.findAllByOrderBySortOrderAscIdAsc()) {
            List<Long> acts = courses.actsByCourse().get(course.getId());
            if (acts == null) {
                continue;
            }
            int questions = acts.stream().mapToInt(id -> perAct.getOrDefault(id, 0L).intValue()).sum();
            if (questions == 0) {
                continue;
            }
            nodes.add(new PoolResponse.CourseNode(
                    course.getId(), course.getName(), course.getSlug(), questions, courses.inPool().contains(course.getId())));
        }
        return nodes;
    }
}
