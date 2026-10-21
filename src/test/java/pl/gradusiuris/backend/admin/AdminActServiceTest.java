package pl.gradusiuris.backend.admin;

import org.junit.jupiter.api.Test;
import pl.gradusiuris.backend.admin.dto.AdminActRequest;
import pl.gradusiuris.backend.admin.dto.AdminActResponse;
import pl.gradusiuris.backend.common.PageParams;
import pl.gradusiuris.backend.common.PageResponse;
import pl.gradusiuris.backend.common.exception.ConflictException;
import pl.gradusiuris.backend.common.exception.ResourceNotFoundException;
import pl.gradusiuris.backend.course.Course;
import pl.gradusiuris.backend.course.CourseRepository;
import pl.gradusiuris.backend.legal.ActSectionRepository;
import pl.gradusiuris.backend.legal.LegalAct;
import pl.gradusiuris.backend.legal.LegalActKind;
import pl.gradusiuris.backend.legal.LegalActRepository;
import pl.gradusiuris.backend.legal.LegalField;
import pl.gradusiuris.backend.legal.LegalFieldRepository;
import pl.gradusiuris.backend.question.QuestionRepository;
import pl.gradusiuris.backend.testsupport.FakeRepos;
import pl.gradusiuris.backend.testsupport.InMemory;
import pl.gradusiuris.backend.track.Track;
import pl.gradusiuris.backend.track.TrackKind;
import pl.gradusiuris.backend.track.TrackRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Akty prawne w panelu administratora na prawdziwym AdminActService, z repozytoriami "w pamięci". */
class AdminActServiceTest {

    private final List<LegalField> fields = new ArrayList<>();
    private final List<LegalAct> acts = new ArrayList<>();
    private final List<Track> tracks = new ArrayList<>();
    private final List<Course> courses = new ArrayList<>();
    private final Map<Long, long[]> questionsByAct = new HashMap<>();
    private final AdminActService service;

    AdminActServiceTest() {
        fields.add(field(1L, "Prawo cywilne i postępowanie cywilne", 10));
        fields.add(field(2L, "Prawo karne i proces karny", 20));
        acts.add(act(1L, fields.get(0), "Kodeks cywilny", "KC", "kodeks-cywilny", 10));
        acts.add(act(2L, fields.get(1), "Kodeks postępowania karnego", "KPK", "kodeks-postepowania-karnego", 20));
        acts.add(act(3L, fields.get(1), "Kodeks karny", "KK", "kodeks-karny", 10));
        acts.add(act(4L, fields.get(1), "Prawo o ruchu drogowym", null, "prawo-o-ruchu-drogowym", 30));
        tracks.add(track(1L, "adwokacka-radcowska", TrackKind.EXAM));
        tracks.add(track(2L, "notarialna", TrackKind.EXAM));
        tracks.add(track(9L, "student", TrackKind.STUDENT));
        courses.add(course(10L, "Prawo karne"));
        tracks.get(0).getActs().add(acts.get(2));
        courses.get(0).getActs().add(acts.get(2));
        tracks.get(1).getActs().add(acts.get(0));

        LegalActRepository actRepository = InMemory.repo(LegalActRepository.class, acts, LegalAct::getId, LegalAct::setId, (method, args) -> switch (method) {
            case "findAllWithField" -> new ArrayList<>(acts);
            case "countBySlugValue" -> acts.stream().filter(a -> a.getSlug().equals(args[0])).count();
            default -> throw new AssertionError("nieoczekiwane: " + method);
        });
        LegalFieldRepository fieldRepository = InMemory.repo(LegalFieldRepository.class, fields, LegalField::getId, LegalField::setId,
                (method, args) -> { throw new AssertionError("nieoczekiwane: " + method); });
        ActSectionRepository sectionRepository = FakeRepos.proxy(ActSectionRepository.class, (method, args) -> {
            assertThat(method).isEqualTo("countByAct");
            return List.of(InMemory.group(3L, 2));
        });
        QuestionRepository questionRepository = FakeRepos.proxy(QuestionRepository.class, (method, args) -> switch (method) {
            case "totalsByAct" -> questionsByAct.entrySet().stream().map(e -> totals(e.getKey(), e.getValue()[0], e.getValue()[1])).toList();
            case "countInAct" -> questionsByAct.containsKey(args[0]) ? questionsByAct.get(args[0])[0] : 0L;
            default -> throw new AssertionError("nieoczekiwane: " + method);
        });
        TrackRepository trackRepository = InMemory.repo(TrackRepository.class, tracks, Track::getId, Track::setId, (method, args) -> {
            assertThat(method).isEqualTo("findAllTrackActs");
            return tracks.stream().flatMap(t -> t.getActs().stream().map(a -> trackAct(t.getId(), a.getId()))).toList();
        });
        CourseRepository courseRepository = InMemory.repo(CourseRepository.class, courses, Course::getId, Course::setId, (method, args) -> {
            assertThat(method).isEqualTo("findAllCourseActs");
            return courses.stream().flatMap(c -> c.getActs().stream().map(a -> courseAct(c.getId(), a.getId()))).toList();
        });
        service = new AdminActService(actRepository, fieldRepository, sectionRepository, questionRepository, trackRepository, courseRepository);
    }

    private static LegalField field(Long id, String name, int order) {
        LegalField field = new LegalField();
        field.setId(id);
        field.setName(name);
        field.setSlug("pole-" + id);
        field.setSortOrder(order);
        return field;
    }

    private static LegalAct act(Long id, LegalField field, String name, String shortName, String slug, int order) {
        LegalAct act = new LegalAct();
        act.setId(id);
        act.setLegalField(field);
        act.setName(name);
        act.setShortName(shortName);
        act.setSlug(slug);
        act.setSortOrder(order);
        return act;
    }

    private static Track track(Long id, String slug, TrackKind kind) {
        Track track = new Track();
        track.setId(id);
        track.setSlug(slug);
        track.setName(slug);
        track.setShortName(slug);
        track.setKind(kind);
        return track;
    }

    private static Course course(Long id, String name) {
        Course course = new Course();
        course.setId(id);
        course.setName(name);
        return course;
    }

    private static QuestionRepository.GroupTotals totals(Long id, long total, long hidden) {
        return new QuestionRepository.GroupTotals() {
            @Override public Long getGroupId() { return id; }
            @Override public Long getTotal() { return total; }
            @Override public Long getHiddenTotal() { return hidden; }
        };
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

    private static AdminActService.ActFilter all() {
        return new AdminActService.ActFilter(null, null, null, null, null, null, false);
    }

    private static AdminActService.ActFilter query(String text) {
        return new AdminActService.ActFilter(text, null, null, null, null, null, false);
    }

    private PageResponse<AdminActResponse> list(AdminActService.ActFilter filter) {
        return service.list(filter, PageParams.of(null, null));
    }

    @Test
    void list_isSortedByFieldThenOrderThenName_andCarriesCountsAndAssignments() {
        questionsByAct.put(3L, new long[]{120, 5});

        PageResponse<AdminActResponse> page = list(all());

        assertThat(page.items()).extracting(AdminActResponse::id).containsExactly(1L, 3L, 2L, 4L);
        AdminActResponse kk = page.items().get(1);
        assertThat(kk.questionCount()).isEqualTo(120);
        assertThat(kk.hiddenQuestionCount()).isEqualTo(5);
        assertThat(kk.sectionCount()).isEqualTo(2);
        assertThat(kk.trackIds()).containsExactly(1L);
        assertThat(kk.courseIds()).containsExactly(10L);
        assertThat(page.items().get(0).questionCount()).isZero();
        assertThat(page.totalElements()).isEqualTo(4);
    }

    @Test
    void list_searchesNameShortNameAndAddress_ignoringCaseAndDiacritics() {
        assertThat(list(query("POSTĘPOWANIA")).items()).extracting(AdminActResponse::id).containsExactly(2L);
        assertThat(list(query("kpk")).items()).extracting(AdminActResponse::id).containsExactly(2L);
        assertThat(list(query("ruchu-drogowym")).items()).extracting(AdminActResponse::id).containsExactly(4L);
        assertThat(list(query("kodeks")).items()).hasSize(3);
        assertThat(list(query("   ")).items()).hasSize(4);
        assertThat(list(query("nie ma takiego")).items()).isEmpty();
    }

    @Test
    void list_filtersByFieldKindHiddenAssignmentAndUnassigned() {
        acts.get(3).setHidden(true);
        acts.get(3).setKind(LegalActKind.TOPIC);

        assertThat(list(new AdminActService.ActFilter(null, 2L, null, null, null, null, false)).items()).hasSize(3);
        assertThat(list(new AdminActService.ActFilter(null, null, LegalActKind.TOPIC, null, null, null, false)).items())
                .extracting(AdminActResponse::id).containsExactly(4L);
        assertThat(list(new AdminActService.ActFilter(null, null, null, true, null, null, false)).items())
                .extracting(AdminActResponse::id).containsExactly(4L);
        assertThat(list(new AdminActService.ActFilter(null, null, null, null, 2L, null, false)).items())
                .extracting(AdminActResponse::id).containsExactly(1L);
        assertThat(list(new AdminActService.ActFilter(null, null, null, null, null, 10L, false)).items())
                .extracting(AdminActResponse::id).containsExactly(3L);
        assertThat(list(new AdminActService.ActFilter(null, null, null, null, null, null, true)).items())
                .extracting(AdminActResponse::id).containsExactly(2L, 4L);
    }

    @Test
    void list_pagesTheResult() {
        PageResponse<AdminActResponse> second = service.list(all(), new PageParams(1, 3));

        assertThat(second.items()).extracting(AdminActResponse::id).containsExactly(4L);
        assertThat(second.totalElements()).isEqualTo(4);
        assertThat(second.totalPages()).isEqualTo(2);
        assertThat(service.list(all(), new PageParams(5, 3)).items()).isEmpty();
    }

    @Test
    void get_ofAMissingAct_isNotFound() {
        assertThatThrownBy(() -> service.get(404L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void options_listsEveryActIncludingHiddenOnes() {
        acts.get(0).setHidden(true);

        assertThat(service.options()).hasSize(4);
        assertThat(service.options().get(0).hidden()).isTrue();
        assertThat(service.options().get(0).slug()).isEqualTo(acts.get(0).getSlug());
    }

    @Test
    void fields_countsTheActsOfEachField() {
        assertThat(service.fields()).extracting("id", "actCount").containsExactly(
                org.assertj.core.groups.Tuple.tuple(1L, 1), org.assertj.core.groups.Tuple.tuple(2L, 3));
    }

    @Test
    void create_addsAnActAtTheEndOfItsField_withAnAddressFromItsName() {
        AdminActResponse created = service.create(new AdminActRequest(2L, "  Kodeks  wykroczeń ", " KW ", null, null));

        assertThat(created.name()).isEqualTo("Kodeks wykroczeń");
        assertThat(created.shortName()).isEqualTo("KW");
        assertThat(created.slug()).isEqualTo("kodeks-wykroczen");
        assertThat(created.kind()).isEqualTo(LegalActKind.ACT);
        assertThat(created.sortOrder()).isEqualTo(40);
        assertThat(created.hidden()).isFalse();
    }

    @Test
    void create_picksAFreeAddress_andTurnsABlankShortNameIntoNone() {
        // ta sama nazwa w innej dziedzinie jest dozwolona, ale adres (slug) jest jeden na całą bazę
        AdminActResponse created = service.create(new AdminActRequest(1L, "Kodeks karny", "  ", LegalActKind.TOPIC, 5));

        assertThat(created.slug()).isEqualTo("kodeks-karny-2");
        assertThat(created.shortName()).isNull();
        assertThat(created.kind()).isEqualTo(LegalActKind.TOPIC);
    }

    @Test
    void create_refusesAnUnknownFieldAndADuplicateNameInTheSameField() {
        assertThatThrownBy(() -> service.create(new AdminActRequest(99L, "X", null, null, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dziedziny");
        assertThatThrownBy(() -> service.create(new AdminActRequest(2L, "KODEKS KARNY", null, null, null)))
                .isInstanceOf(ConflictException.class);
        assertThat(acts).hasSize(4);
    }

    @Test
    void update_changesTheActButNotItsAddress_andMayMoveItToAnotherField() {
        AdminActResponse updated = service.update(4L, new AdminActRequest(1L, "Ustawa o ruchu drogowym", "PORD", LegalActKind.ACT, 99));

        assertThat(updated.slug()).isEqualTo("prawo-o-ruchu-drogowym");
        assertThat(updated.name()).isEqualTo("Ustawa o ruchu drogowym");
        assertThat(updated.fieldId()).isEqualTo(1L);
        assertThat(updated.sortOrder()).isEqualTo(99);
    }

    @Test
    void update_keepsKindAndOrderWhenNoneAreGiven_andAllowsItsOwnName() {
        acts.get(3).setKind(LegalActKind.TOPIC);

        AdminActResponse updated = service.update(4L, new AdminActRequest(2L, "Prawo o ruchu drogowym", null, null, null));

        assertThat(updated.kind()).isEqualTo(LegalActKind.TOPIC);
        assertThat(updated.sortOrder()).isEqualTo(30);
    }

    @Test
    void setHidden_hidesAndRestores() {
        assertThat(service.setHidden(3L, true).hidden()).isTrue();
        assertThat(acts.get(2).isHidden()).isTrue();
        assertThat(service.setHidden(3L, false).hidden()).isFalse();
    }

    @Test
    void delete_removesAnActWithoutQuestions() {
        service.delete(4L);

        assertThat(acts).extracting(LegalAct::getId).containsExactly(1L, 2L, 3L);
    }

    @Test
    void delete_refusesAnActThatHasQuestions_evenIfAllAreHidden() {
        questionsByAct.put(3L, new long[]{3, 3});

        assertThatThrownBy(() -> service.delete(3L))
                .isInstanceOf(ConflictException.class).hasMessageContaining("Ukryj");
        assertThat(acts).hasSize(4);
    }

    @Test
    void setAssignments_putsTheActInExactlyTheChosenApplicationsAndCourses() {
        AdminActResponse updated = service.setAssignments(3L, List.of(2L), List.of());

        assertThat(updated.trackIds()).containsExactly(2L);
        assertThat(updated.courseIds()).isEmpty();
        // aplikacja 1 straciła akt, a inne akty aplikacji 2 zostały
        assertThat(tracks.get(0).getActs()).isEmpty();
        assertThat(tracks.get(1).getActs()).extracting(LegalAct::getId).containsExactlyInAnyOrder(1L, 3L);
    }

    @Test
    void setAssignments_refusesTheStudentTrackAndUnknownTargets_withoutChangingAnything() {
        assertThatThrownBy(() -> service.setAssignments(3L, List.of(9L), List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("przedmiot");
        assertThatThrownBy(() -> service.setAssignments(3L, List.of(77L), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.setAssignments(3L, List.of(), List.of(77L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(tracks.get(0).getActs()).extracting(LegalAct::getId).containsExactly(3L);
        assertThat(courses.get(0).getActs()).extracting(LegalAct::getId).containsExactly(3L);
    }
}
