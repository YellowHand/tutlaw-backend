package pl.tutlaw.backend.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tutlaw.backend.admin.dto.AdminCourseRequest;
import pl.tutlaw.backend.admin.dto.AdminCourseResponse;
import pl.tutlaw.backend.common.exception.ConflictException;
import pl.tutlaw.backend.common.exception.ResourceNotFoundException;
import pl.tutlaw.backend.course.Course;
import pl.tutlaw.backend.course.CourseRepository;
import pl.tutlaw.backend.legal.LegalAct;
import pl.tutlaw.backend.pool.UserCourseRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Przedmioty Studenta w panelu administratora: dodawanie, zmiana nazwy i kolejności, usuwanie oraz lista aktów
 * i zagadnień przedmiotu. Student wybiera przedmioty, a jego pula to akty wybranych przedmiotów. Usunięcie przedmiotu
 * zdejmuje go z wyborów studentów (pula studenta, któremu nic nie zostało, wraca do wszystkich przedmiotów).
 */
@Service
@RequiredArgsConstructor
public class AdminCourseService {

    private static final int SLUG_MAX = 100;

    private final CourseRepository courseRepository;
    private final UserCourseRepository userCourseRepository;
    private final AdminTrackService trackService;

    @Transactional(readOnly = true)
    public List<AdminCourseResponse> list() {
        Map<Long, List<Long>> actsByCourse = new HashMap<>();
        for (CourseRepository.CourseAct pair : courseRepository.findAllCourseActs()) {
            actsByCourse.computeIfAbsent(pair.getCourseId(), k -> new ArrayList<>()).add(pair.getActId());
        }
        Map<Long, Long> users = AdminMaps.counts(userCourseRepository.countByCourse());
        List<AdminCourseResponse> result = new ArrayList<>();
        for (Course course : courseRepository.findAllByOrderBySortOrderAscIdAsc()) {
            result.add(new AdminCourseResponse(
                    course.getId(), course.getSlug(), course.getName(), course.getSortOrder(),
                    actsByCourse.getOrDefault(course.getId(), List.of()).stream().sorted().toList(),
                    users.getOrDefault(course.getId(), 0L)));
        }
        return result;
    }

    @Transactional
    public AdminCourseResponse create(AdminCourseRequest request) {
        String name = clean(request.name());
        List<Course> all = courseRepository.findAll();
        requireFreeName(all, null, name);

        Course course = new Course();
        course.setName(name);
        course.setSlug(Slugs.unique(name, SLUG_MAX, slug -> all.stream().anyMatch(c -> c.getSlug().equals(slug))));
        course.setSortOrder(request.sortOrder() != null ? request.sortOrder()
                : all.stream().mapToInt(Course::getSortOrder).max().orElse(0) + AdminTrackService.SORT_STEP);
        courseRepository.save(course);
        return describe(course.getId());
    }

    @Transactional
    public AdminCourseResponse update(Long id, AdminCourseRequest request) {
        Course course = find(id);
        String name = clean(request.name());
        requireFreeName(courseRepository.findAll(), id, name);
        course.setName(name);
        if (request.sortOrder() != null) {
            course.setSortOrder(request.sortOrder());
        }
        courseRepository.save(course);
        return describe(id);
    }

    @Transactional
    public void delete(Long id) {
        courseRepository.delete(find(id));
    }

    /** Zastępuje listę aktów i zagadnień przedmiotu. */
    @Transactional
    public AdminCourseResponse setActs(Long id, List<Long> actIds) {
        Course course = find(id);
        List<LegalAct> acts = trackService.resolveActs(actIds);
        course.getActs().clear();
        course.getActs().addAll(acts);
        courseRepository.save(course);
        return describe(id);
    }

    private AdminCourseResponse describe(Long id) {
        return list().stream().filter(c -> c.id().equals(id)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Nie ma takiego przedmiotu."));
    }

    private Course find(Long id) {
        return courseRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Nie ma takiego przedmiotu."));
    }

    private static void requireFreeName(List<Course> all, Long selfId, String name) {
        for (Course other : all) {
            if (!other.getId().equals(selfId) && Slugs.fold(other.getName()).equals(Slugs.fold(name))) {
                throw new ConflictException("Jest już przedmiot o takiej nazwie.");
            }
        }
    }

    private static String clean(String text) {
        String trimmed = text == null ? "" : text.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Nazwa nie może być pusta.");
        }
        return trimmed;
    }
}
