package pl.tutlaw.backend.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tutlaw.backend.admin.dto.AdminActOptionResponse;
import pl.tutlaw.backend.admin.dto.AdminActRequest;
import pl.tutlaw.backend.admin.dto.AdminActResponse;
import pl.tutlaw.backend.admin.dto.AdminFieldResponse;
import pl.tutlaw.backend.common.PageParams;
import pl.tutlaw.backend.common.PageResponse;
import pl.tutlaw.backend.common.PolishSort;
import pl.tutlaw.backend.common.exception.ConflictException;
import pl.tutlaw.backend.common.exception.ResourceNotFoundException;
import pl.tutlaw.backend.course.Course;
import pl.tutlaw.backend.course.CourseRepository;
import pl.tutlaw.backend.legal.ActSectionRepository;
import pl.tutlaw.backend.legal.LegalAct;
import pl.tutlaw.backend.legal.LegalActKind;
import pl.tutlaw.backend.legal.LegalActRepository;
import pl.tutlaw.backend.legal.LegalField;
import pl.tutlaw.backend.legal.LegalFieldRepository;
import pl.tutlaw.backend.question.QuestionRepository;
import pl.tutlaw.backend.track.Track;
import pl.tutlaw.backend.track.TrackRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Akty prawne i zagadnienia w panelu administratora: lista z wyszukiwaniem i filtrami, dodawanie, zmiana, ukrywanie,
 * usuwanie (tylko aktu bez pytań) i przypisywanie aktu do aplikacji oraz przedmiotów Studenta. Tabele są małe (setki
 * aktów), więc lista składa się w pamięci z kilku zapytań grupujących; koszt nie rośnie z liczbą pytań.
 */
@Service
@RequiredArgsConstructor
public class AdminActService {

    private static final int SLUG_MAX = 100;

    private final LegalActRepository actRepository;
    private final LegalFieldRepository fieldRepository;
    private final ActSectionRepository sectionRepository;
    private final QuestionRepository questionRepository;
    private final TrackRepository trackRepository;
    private final CourseRepository courseRepository;

    /** Filtry listy aktów; {@code null} (albo pusty tekst) znaczy „bez ograniczenia". */
    public record ActFilter(String query, Long fieldId, LegalActKind kind, Boolean hidden, Long trackId, Long courseId,
                            boolean unassigned) {
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminActResponse> list(ActFilter filter, PageParams page) {
        String needle = Slugs.fold(filter.query() == null ? "" : filter.query().trim());
        List<AdminActResponse> matching = describeAll().stream()
                .filter(a -> needle.isEmpty() || matches(a, needle))
                .filter(a -> filter.fieldId() == null || a.fieldId().equals(filter.fieldId()))
                .filter(a -> filter.kind() == null || a.kind() == filter.kind())
                .filter(a -> filter.hidden() == null || a.hidden() == filter.hidden())
                .filter(a -> filter.trackId() == null || a.trackIds().contains(filter.trackId()))
                .filter(a -> filter.courseId() == null || a.courseIds().contains(filter.courseId()))
                .filter(a -> !filter.unassigned() || (a.trackIds().isEmpty() && a.courseIds().isEmpty()))
                .toList();
        int from = (int) Math.min((long) page.page() * page.size(), matching.size());
        int to = Math.min(from + page.size(), matching.size());
        int totalPages = (int) Math.ceil(matching.size() / (double) page.size());
        return new PageResponse<>(matching.subList(from, to), page.page(), page.size(), matching.size(), totalPages);
    }

    @Transactional(readOnly = true)
    public AdminActResponse get(Long id) {
        return describeAll().stream().filter(a -> a.id().equals(id)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Nie ma takiego aktu."));
    }

    /** Wszystkie akty (także ukryte) w lekkiej postaci: do list wyboru przy przypisywaniu aktów. */
    @Transactional(readOnly = true)
    public List<AdminActOptionResponse> options() {
        return sortedActs().stream()
                .map(a -> new AdminActOptionResponse(a.getId(), a.getSlug(), a.getName(), a.getShortName(),
                        a.getLegalField().getId(), a.getLegalField().getName(), a.getKind(), a.isHidden()))
                .toList();
    }

    /** Dziedziny prawa (tylko do odczytu) z liczbą aktów, w kolejności z bazy. */
    @Transactional(readOnly = true)
    public List<AdminFieldResponse> fields() {
        Map<Long, Integer> perField = new HashMap<>();
        for (LegalAct act : actRepository.findAllWithField()) {
            perField.merge(act.getLegalField().getId(), 1, Integer::sum);
        }
        return fieldRepository.findAll().stream()
                .sorted(Comparator.comparingInt(LegalField::getSortOrder).thenComparing(LegalField::getId))
                .map(f -> new AdminFieldResponse(f.getId(), f.getName(), f.getSortOrder(), perField.getOrDefault(f.getId(), 0)))
                .toList();
    }

    @Transactional
    public AdminActResponse create(AdminActRequest request) {
        LegalField field = findField(request.fieldId());
        String name = clean(request.name());
        List<LegalAct> all = actRepository.findAllWithField();
        requireFreeName(all, null, field.getId(), name);

        LegalAct act = new LegalAct();
        act.setLegalField(field);
        act.setName(name);
        act.setShortName(blankToNull(request.shortName()));
        act.setKind(request.kind() != null ? request.kind() : LegalActKind.ACT);
        act.setSlug(Slugs.unique(name, SLUG_MAX, slug -> actRepository.countBySlugValue(slug) > 0));
        act.setSortOrder(request.sortOrder() != null ? request.sortOrder()
                : all.stream().filter(a -> a.getLegalField().getId().equals(field.getId()))
                        .mapToInt(LegalAct::getSortOrder).max().orElse(0) + AdminTrackService.SORT_STEP);
        actRepository.save(act);
        return get(act.getId());
    }

    @Transactional
    public AdminActResponse update(Long id, AdminActRequest request) {
        LegalAct act = find(id);
        LegalField field = findField(request.fieldId());
        String name = clean(request.name());
        requireFreeName(actRepository.findAllWithField(), id, field.getId(), name);
        act.setLegalField(field);
        act.setName(name);
        act.setShortName(blankToNull(request.shortName()));
        if (request.kind() != null) {
            act.setKind(request.kind());
        }
        if (request.sortOrder() != null) {
            act.setSortOrder(request.sortOrder());
        }
        actRepository.save(act);
        return get(id);
    }

    /** Ukrywa albo przywraca akt: ukryty znika wszystkim użytkownikom, a pytania i historia zostają. */
    @Transactional
    public AdminActResponse setHidden(Long id, boolean hidden) {
        LegalAct act = find(id);
        act.setHidden(hidden);
        actRepository.save(act);
        return get(id);
    }

    /**
     * Usuwa akt tylko wtedy, gdy nie ma żadnych pytań (także ukrytych). Z aktem znikają jego rozdziały oraz przypisania
     * do aplikacji i przedmiotów (robi to baza). Akt z pytaniami można tylko ukryć.
     */
    @Transactional
    public void delete(Long id) {
        LegalAct act = find(id);
        long questions = questionRepository.countInAct(id);
        if (questions > 0) {
            throw new ConflictException("Akt ma pytania (" + questions + "), więc nie można go usunąć. Ukryj go zamiast tego.");
        }
        actRepository.delete(act);
    }

    /** Ustawia, do jakich aplikacji i przedmiotów Studenta należy akt (zastępuje dotychczasowe przypisania). */
    @Transactional
    public AdminActResponse setAssignments(Long id, List<Long> trackIds, List<Long> courseIds) {
        LegalAct act = find(id);
        Set<Long> wantedTracks = new HashSet<>(trackIds);
        Set<Long> wantedCourses = new HashSet<>(courseIds);

        List<Track> tracks = trackRepository.findAll();
        Set<Long> knownTracks = new HashSet<>();
        for (Track track : tracks) {
            knownTracks.add(track.getId());
            if (wantedTracks.contains(track.getId()) && track.isStudent()) {
                throw new IllegalArgumentException("Akty ścieżki Student przypisuje się przez przedmioty.");
            }
        }
        List<Course> courses = courseRepository.findAll();
        Set<Long> knownCourses = new HashSet<>();
        for (Course course : courses) {
            knownCourses.add(course.getId());
        }
        if (!knownTracks.containsAll(wantedTracks) || !knownCourses.containsAll(wantedCourses)) {
            throw new IllegalArgumentException("Wybrano aplikację albo przedmiot, którego nie ma w bazie. Odśwież stronę i spróbuj ponownie.");
        }

        for (Track track : tracks) {
            if (!track.isStudent()) {
                toggle(track.getActs(), act, wantedTracks.contains(track.getId()));
            }
        }
        for (Course course : courses) {
            toggle(course.getActs(), act, wantedCourses.contains(course.getId()));
        }
        trackRepository.saveAll(tracks);
        courseRepository.saveAll(courses);
        return get(id);
    }

    private static void toggle(Set<LegalAct> acts, LegalAct act, boolean wanted) {
        boolean has = acts.stream().anyMatch(a -> a.getId().equals(act.getId()));
        if (wanted && !has) {
            acts.add(act);
        } else if (!wanted && has) {
            acts.removeIf(a -> a.getId().equals(act.getId()));
        }
    }

    private List<LegalAct> sortedActs() {
        return actRepository.findAllWithField().stream()
                .sorted(Comparator.<LegalAct>comparingInt(a -> a.getLegalField().getSortOrder())
                        .thenComparing(a -> a.getLegalField().getId())
                        .thenComparingInt(LegalAct::getSortOrder)
                        .thenComparing(LegalAct::getName, PolishSort.byName())
                        .thenComparing(LegalAct::getId))
                .toList();
    }

    private List<AdminActResponse> describeAll() {
        Map<Long, QuestionRepository.GroupTotals> totals = AdminMaps.totals(questionRepository.totalsByAct());
        Map<Long, Long> sections = AdminMaps.counts(sectionRepository.countByAct());
        Map<Long, List<Long>> tracksByAct = new HashMap<>();
        for (TrackRepository.TrackAct pair : trackRepository.findAllTrackActs()) {
            tracksByAct.computeIfAbsent(pair.getActId(), k -> new ArrayList<>()).add(pair.getTrackId());
        }
        Map<Long, List<Long>> coursesByAct = new HashMap<>();
        for (CourseRepository.CourseAct pair : courseRepository.findAllCourseActs()) {
            coursesByAct.computeIfAbsent(pair.getActId(), k -> new ArrayList<>()).add(pair.getCourseId());
        }

        List<AdminActResponse> result = new ArrayList<>();
        for (LegalAct act : sortedActs()) {
            Long id = act.getId();
            result.add(new AdminActResponse(
                    id, act.getSlug(), act.getName(), act.getShortName(),
                    act.getLegalField().getId(), act.getLegalField().getName(),
                    act.getKind(), act.getSortOrder(), act.isHidden(),
                    AdminMaps.total(totals, id), AdminMaps.hidden(totals, id),
                    sections.getOrDefault(id, 0L).intValue(),
                    tracksByAct.getOrDefault(id, List.of()).stream().sorted().toList(),
                    coursesByAct.getOrDefault(id, List.of()).stream().sorted().toList()));
        }
        return result;
    }

    private static boolean matches(AdminActResponse act, String needle) {
        return Slugs.fold(act.name()).contains(needle)
                || Slugs.fold(act.shortName()).contains(needle)
                || Slugs.fold(act.slug()).contains(needle);
    }

    private LegalAct find(Long id) {
        return actRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Nie ma takiego aktu."));
    }

    private LegalField findField(Long id) {
        return fieldRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Nie ma takiej dziedziny."));
    }

    private static void requireFreeName(List<LegalAct> all, Long selfId, Long fieldId, String name) {
        for (LegalAct other : all) {
            if (!other.getId().equals(selfId) && other.getLegalField().getId().equals(fieldId)
                    && Slugs.fold(other.getName()).equals(Slugs.fold(name))) {
                throw new ConflictException("W tej dziedzinie jest już akt o takiej nazwie.");
            }
        }
    }

    private static String blankToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim().replaceAll("\\s+", " ");
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String clean(String text) {
        String trimmed = text == null ? "" : text.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Nazwa nie może być pusta.");
        }
        return trimmed;
    }
}
