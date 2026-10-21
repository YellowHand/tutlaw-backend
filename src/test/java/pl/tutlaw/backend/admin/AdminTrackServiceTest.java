package pl.tutlaw.backend.admin;

import org.junit.jupiter.api.Test;
import pl.tutlaw.backend.admin.dto.AdminTrackRequest;
import pl.tutlaw.backend.admin.dto.AdminTrackResponse;
import pl.tutlaw.backend.common.exception.ConflictException;
import pl.tutlaw.backend.common.exception.ResourceNotFoundException;
import pl.tutlaw.backend.course.Course;
import pl.tutlaw.backend.course.CourseRepository;
import pl.tutlaw.backend.legal.LegalAct;
import pl.tutlaw.backend.legal.LegalActRepository;
import pl.tutlaw.backend.settings.ExamTerm;
import pl.tutlaw.backend.settings.ExamTermRepository;
import pl.tutlaw.backend.settings.UserSettingsRepository;
import pl.tutlaw.backend.source.QuestionSourceRepository;
import pl.tutlaw.backend.testsupport.InMemory;
import pl.tutlaw.backend.track.Track;
import pl.tutlaw.backend.track.TrackKind;
import pl.tutlaw.backend.track.TrackRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Aplikacje w panelu administratora na prawdziwym AdminTrackService, z repozytoriami "w pamięci". */
class AdminTrackServiceTest {

    private final List<Track> tracks = new ArrayList<>();
    private final List<LegalAct> acts = new ArrayList<>();
    private final List<Course> courses = new ArrayList<>();
    private final List<ExamTerm> terms = new ArrayList<>();
    private final Map<Long, Long> sheetsByTrack = new HashMap<>();
    private final Map<Long, Long> usersByTrack = new HashMap<>();
    private final AdminTrackService service;

    AdminTrackServiceTest() {
        tracks.add(track(1L, "adwokacka-radcowska", "Aplikacja adwokacka i radcowska", "Adwokacka i radcowska", TrackKind.EXAM, 10));
        tracks.add(track(2L, "notarialna", "Aplikacja notarialna", "Notarialna", TrackKind.EXAM, 20));
        tracks.add(track(9L, "student", "Student", "Student", TrackKind.STUDENT, 60));
        for (long id = 1; id <= 3; id++) {
            LegalAct act = new LegalAct();
            act.setId(id);
            act.setName("Akt " + id);
            acts.add(act);
        }
        tracks.get(0).getActs().add(acts.get(0));
        Course course = new Course();
        course.setId(10L);
        course.setName("Prawo karne");
        course.getActs().add(acts.get(2));
        courses.add(course);
        ExamTerm term = new ExamTerm(1L);
        term.setExamDate(LocalDate.of(2027, 3, 14));
        terms.add(term);
        terms.add(new ExamTerm(2L));

        TrackRepository trackRepository = InMemory.repo(TrackRepository.class, tracks, Track::getId, Track::setId, (method, args) -> switch (method) {
            case "findAllByOrderBySortOrderAscIdAsc" -> tracks.stream()
                    .sorted(Comparator.comparingInt(Track::getSortOrder).thenComparing(Track::getId)).toList();
            case "findAllTrackActs" -> tracks.stream().flatMap(t -> t.getActs().stream().map(a -> trackAct(t.getId(), a.getId()))).toList();
            default -> throw new AssertionError("nieoczekiwane: " + method);
        });
        LegalActRepository actRepository = InMemory.repo(LegalActRepository.class, acts, LegalAct::getId, LegalAct::setId,
                (method, args) -> { throw new AssertionError("nieoczekiwane: " + method); });
        CourseRepository courseRepository = InMemory.repo(CourseRepository.class, courses, Course::getId, Course::setId, (method, args) -> {
            assertThat(method).isEqualTo("findAllCourseActs");
            return courses.stream().flatMap(c -> c.getActs().stream().map(a -> courseAct(c.getId(), a.getId()))).toList();
        });
        QuestionSourceRepository sourceRepository = pl.tutlaw.backend.testsupport.FakeRepos.proxy(QuestionSourceRepository.class, (method, args) -> switch (method) {
            case "countSheetsByTrack" -> sheetsByTrack.entrySet().stream().map(e -> InMemory.group(e.getKey(), e.getValue())).toList();
            case "countSheetsOfTrack" -> sheetsByTrack.getOrDefault((Long) args[0], 0L);
            default -> throw new AssertionError("nieoczekiwane: " + method);
        });
        ExamTermRepository examTermRepository = InMemory.repo(ExamTermRepository.class, terms, ExamTerm::getTrackId, ExamTerm::setTrackId,
                (method, args) -> { throw new AssertionError("nieoczekiwane: " + method); });
        UserSettingsRepository settingsRepository = pl.tutlaw.backend.testsupport.FakeRepos.proxy(UserSettingsRepository.class, (method, args) -> switch (method) {
            case "countByTrack" -> usersByTrack.entrySet().stream().map(e -> InMemory.group(e.getKey(), e.getValue())).toList();
            case "countUsingTrack" -> usersByTrack.getOrDefault((Long) args[0], 0L);
            default -> throw new AssertionError("nieoczekiwane: " + method);
        });
        service = new AdminTrackService(trackRepository, actRepository, courseRepository, sourceRepository, examTermRepository, settingsRepository);
    }

    private static Track track(Long id, String slug, String name, String shortName, TrackKind kind, int order) {
        Track track = new Track();
        track.setId(id);
        track.setSlug(slug);
        track.setName(name);
        track.setShortName(shortName);
        track.setKind(kind);
        track.setSortOrder(order);
        return track;
    }

    private static TrackRepository.TrackAct trackAct(Long trackId, Long actId) {
        return new TrackRepository.TrackAct() {
            @Override public Long getTrackId() { return trackId; }
            @Override public Long getActId() { return actId; }
        };
    }

    private static CourseRepository.CourseAct courseAct(Long courseId, Long actId) {
        return new CourseRepository.CourseAct() {
            @Override public Long getCourseId() { return courseId; }
            @Override public Long getActId() { return actId; }
        };
    }

    private AdminTrackResponse byId(List<AdminTrackResponse> list, long id) {
        return list.stream().filter(t -> t.id() == id).findFirst().orElseThrow();
    }

    @Test
    void list_showsActsSheetsUsersAndTheExamDate_andTheStudentsActsComeFromItsCourses() {
        sheetsByTrack.put(1L, 7L);
        usersByTrack.put(1L, 12L);

        List<AdminTrackResponse> list = service.list();

        assertThat(list).extracting(AdminTrackResponse::id).containsExactly(1L, 2L, 9L);
        AdminTrackResponse adwokacka = byId(list, 1);
        assertThat(adwokacka.actIds()).containsExactly(1L);
        assertThat(adwokacka.sheetCount()).isEqualTo(7);
        assertThat(adwokacka.userCount()).isEqualTo(12);
        assertThat(adwokacka.examDate()).isEqualTo(LocalDate.of(2027, 3, 14));
        assertThat(byId(list, 2).examDate()).isNull();
        AdminTrackResponse student = byId(list, 9);
        assertThat(student.actIds()).containsExactly(3L);
        assertThat(student.examDate()).isNull();
    }

    @Test
    void create_addsAnExamTrackAtTheEnd_withAnAddressFromItsName_andAnEmptyExamTerm() {
        AdminTrackResponse created = service.create(new AdminTrackRequest("  Aplikacja  testowa ", "Testowa", null));

        assertThat(created.kind()).isEqualTo(TrackKind.EXAM);
        assertThat(created.name()).isEqualTo("Aplikacja testowa");
        assertThat(created.slug()).isEqualTo("aplikacja-testowa");
        assertThat(created.sortOrder()).isEqualTo(70);
        assertThat(terms).extracting(ExamTerm::getTrackId).contains(created.id());
        assertThat(created.examDate()).isNull();
    }

    @Test
    void create_refusesANameOrShortNameThatIsTakenIgnoringCaseAndDiacritics() {
        assertThatThrownBy(() -> service.create(new AdminTrackRequest("APLIKACJA NOTARIALNA", "Inna", null)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("nazwie");
        assertThatThrownBy(() -> service.create(new AdminTrackRequest("Zupełnie inna", "notarialna", null)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("krótkiej");
        assertThat(tracks).hasSize(3);
    }

    @Test
    void update_changesNamesAndOrder_butNotTheAddress() {
        AdminTrackResponse updated = service.update(2L, new AdminTrackRequest("Aplikacja notarialna 2027", "Notarialna", 5));

        assertThat(updated.name()).isEqualTo("Aplikacja notarialna 2027");
        assertThat(updated.slug()).isEqualTo("notarialna");
        assertThat(updated.sortOrder()).isEqualTo(5);
        assertThat(service.list()).extracting(AdminTrackResponse::id).containsExactly(2L, 1L, 9L);
    }

    @Test
    void update_keepsTheOrderWhenNoneIsGiven_andAllowsKeepingItsOwnName() {
        AdminTrackResponse updated = service.update(2L, new AdminTrackRequest("Aplikacja notarialna", "Notarialna", null));

        assertThat(updated.sortOrder()).isEqualTo(20);
    }

    @Test
    void update_ofAMissingTrack_isNotFound() {
        assertThatThrownBy(() -> service.update(404L, new AdminTrackRequest("X", "X", null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_removesAnEmptyApplication() {
        service.delete(2L);

        assertThat(tracks).extracting(Track::getId).containsExactly(1L, 9L);
    }

    @Test
    void delete_refusesTheStudentTrack_anApplicationWithSheets_andOneThatSomeoneChose() {
        sheetsByTrack.put(1L, 2L);
        usersByTrack.put(2L, 1L);

        assertThatThrownBy(() -> service.delete(9L)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Student");
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(ConflictException.class).hasMessageContaining("arkusze");
        assertThatThrownBy(() -> service.delete(2L)).isInstanceOf(ConflictException.class).hasMessageContaining("wybrało");
        assertThat(tracks).hasSize(3);
    }

    @Test
    void setActs_replacesTheListAndIgnoresDuplicates() {
        AdminTrackResponse updated = service.setActs(1L, List.of(2L, 3L, 3L));

        assertThat(updated.actIds()).containsExactly(2L, 3L);
        assertThat(service.setActs(1L, List.of()).actIds()).isEmpty();
    }

    @Test
    void setActs_refusesAnUnknownActAndTheStudentTrack() {
        assertThatThrownBy(() -> service.setActs(1L, List.of(1L, 99L))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.setActs(9L, List.of(1L))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("przedmiot");
        assertThat(tracks.get(0).getActs()).extracting(LegalAct::getId).containsExactly(1L);
    }
}
