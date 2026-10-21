package pl.gradusiuris.backend.admin;

import org.junit.jupiter.api.Test;
import pl.gradusiuris.backend.admin.dto.AdminCourseRequest;
import pl.gradusiuris.backend.admin.dto.AdminCourseResponse;
import pl.gradusiuris.backend.common.exception.ConflictException;
import pl.gradusiuris.backend.common.exception.ResourceNotFoundException;
import pl.gradusiuris.backend.course.Course;
import pl.gradusiuris.backend.course.CourseRepository;
import pl.gradusiuris.backend.legal.LegalAct;
import pl.gradusiuris.backend.legal.LegalActRepository;
import pl.gradusiuris.backend.pool.UserCourseRepository;
import pl.gradusiuris.backend.settings.ExamTermRepository;
import pl.gradusiuris.backend.settings.UserSettingsRepository;
import pl.gradusiuris.backend.source.QuestionSourceRepository;
import pl.gradusiuris.backend.testsupport.FakeRepos;
import pl.gradusiuris.backend.testsupport.InMemory;
import pl.gradusiuris.backend.track.Track;
import pl.gradusiuris.backend.track.TrackRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Przedmioty Studenta w panelu administratora na prawdziwym AdminCourseService, z repozytoriami "w pamięci". */
class AdminCourseServiceTest {

    private final List<Course> courses = new ArrayList<>();
    private final List<LegalAct> acts = new ArrayList<>();
    private final Map<Long, Long> usersByCourse = new HashMap<>();
    private final AdminCourseService service;

    AdminCourseServiceTest() {
        for (long id = 1; id <= 3; id++) {
            LegalAct act = new LegalAct();
            act.setId(id);
            act.setName("Akt " + id);
            acts.add(act);
        }
        courses.add(course(10L, "Prawo karne", 10, acts.get(0)));
        courses.add(course(11L, "Prawoznawstwo", 20, acts.get(1)));

        CourseRepository courseRepository = InMemory.repo(CourseRepository.class, courses, Course::getId, Course::setId, (method, args) -> switch (method) {
            case "findAllByOrderBySortOrderAscIdAsc" -> courses.stream()
                    .sorted(Comparator.comparingInt(Course::getSortOrder).thenComparing(Course::getId)).toList();
            case "findAllCourseActs" -> courses.stream().flatMap(c -> c.getActs().stream().map(a -> courseAct(c.getId(), a.getId()))).toList();
            default -> throw new AssertionError("nieoczekiwane: " + method);
        });
        UserCourseRepository userCourseRepository = FakeRepos.proxy(UserCourseRepository.class, (method, args) -> {
            assertThat(method).isEqualTo("countByCourse");
            return usersByCourse.entrySet().stream().map(e -> InMemory.group(e.getKey(), e.getValue())).toList();
        });
        LegalActRepository actRepository = InMemory.repo(LegalActRepository.class, acts, LegalAct::getId, LegalAct::setId,
                (method, args) -> { throw new AssertionError("nieoczekiwane: " + method); });
        AdminTrackService trackService = new AdminTrackService(
                InMemory.repo(TrackRepository.class, new ArrayList<Track>(), Track::getId, Track::setId, (m, a) -> { throw new AssertionError(m); }),
                actRepository, courseRepository,
                FakeRepos.proxy(QuestionSourceRepository.class, (m, a) -> { throw new AssertionError(m); }),
                FakeRepos.proxy(ExamTermRepository.class, (m, a) -> { throw new AssertionError(m); }),
                FakeRepos.proxy(UserSettingsRepository.class, (m, a) -> { throw new AssertionError(m); }));
        service = new AdminCourseService(courseRepository, userCourseRepository, trackService);
    }

    private static Course course(Long id, String name, int order, LegalAct act) {
        Course course = new Course();
        course.setId(id);
        course.setName(name);
        course.setSlug(name.toLowerCase().replace(' ', '-'));
        course.setSortOrder(order);
        course.getActs().add(act);
        return course;
    }

    private static CourseRepository.CourseAct courseAct(Long courseId, Long actId) {
        return new CourseRepository.CourseAct() {
            @Override public Long getCourseId() { return courseId; }
            @Override public Long getActId() { return actId; }
        };
    }

    @Test
    void list_showsActsAndHowManyStudentsChoseEachCourse() {
        usersByCourse.put(10L, 4L);

        List<AdminCourseResponse> list = service.list();

        assertThat(list).extracting(AdminCourseResponse::name).containsExactly("Prawo karne", "Prawoznawstwo");
        assertThat(list.get(0).actIds()).containsExactly(1L);
        assertThat(list.get(0).userCount()).isEqualTo(4);
        assertThat(list.get(1).userCount()).isZero();
    }

    @Test
    void create_addsACourseAtTheEndWithAnAddressFromItsName() {
        AdminCourseResponse created = service.create(new AdminCourseRequest(" Historia  prawa ", null));

        assertThat(created.name()).isEqualTo("Historia prawa");
        assertThat(created.slug()).isEqualTo("historia-prawa");
        assertThat(created.sortOrder()).isEqualTo(30);
        assertThat(created.actIds()).isEmpty();
    }

    @Test
    void create_refusesATakenName_ignoringCaseAndDiacritics() {
        assertThatThrownBy(() -> service.create(new AdminCourseRequest("PRAWOZNAWSTWO", null)))
                .isInstanceOf(ConflictException.class);
        assertThat(courses).hasSize(2);
    }

    @Test
    void update_renamesAndReorders() {
        AdminCourseResponse updated = service.update(11L, new AdminCourseRequest("Teoria prawa", 1));

        assertThat(updated.name()).isEqualTo("Teoria prawa");
        assertThat(updated.slug()).isEqualTo("prawoznawstwo");
        assertThat(service.list()).extracting(AdminCourseResponse::id).containsExactly(11L, 10L);
    }

    @Test
    void delete_removesTheCourse_andAMissingOneIsNotFound() {
        service.delete(10L);

        assertThat(courses).extracting(Course::getId).containsExactly(11L);
        assertThatThrownBy(() -> service.delete(10L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void setActs_replacesTheActsOfTheCourse() {
        AdminCourseResponse updated = service.setActs(10L, List.of(2L, 3L));

        assertThat(updated.actIds()).containsExactly(2L, 3L);
        assertThatThrownBy(() -> service.setActs(10L, List.of(77L))).isInstanceOf(IllegalArgumentException.class);
    }
}
