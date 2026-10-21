package pl.tutlaw.backend.pool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.security.core.Authentication;
import pl.tutlaw.backend.common.GroupCount;
import pl.tutlaw.backend.course.Course;
import pl.tutlaw.backend.course.CourseRepository;
import pl.tutlaw.backend.legal.LegalAct;
import pl.tutlaw.backend.legal.LegalActRepository;
import pl.tutlaw.backend.legal.LegalFieldRepository;
import pl.tutlaw.backend.pool.dto.PoolResponse;
import pl.tutlaw.backend.pool.dto.UpdatePoolActsRequest;
import pl.tutlaw.backend.pool.dto.UpdatePoolCoursesRequest;
import pl.tutlaw.backend.question.QuestionRepository;
import pl.tutlaw.backend.testsupport.Pools;
import pl.tutlaw.backend.testsupport.QuestionRefs;
import pl.tutlaw.backend.track.Track;
import pl.tutlaw.backend.track.TrackKind;
import pl.tutlaw.backend.track.TrackRepository;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Okno „Pula pytań": zawężenie do aktów (student: do przedmiotów). Pula bazowa: akty 1 i 2, źródła 1-3. */
class PoolSettingsServiceTest {

    private static final long USER = 7L;

    private final UserPoolActRepository poolActRepository = mock(UserPoolActRepository.class);
    private final UserCourseRepository userCourseRepository = mock(UserCourseRepository.class);
    private final CourseRepository courseRepository = mock(CourseRepository.class);
    private final TrackRepository trackRepository = mock(TrackRepository.class);
    private final LegalFieldRepository fieldRepository = mock(LegalFieldRepository.class);
    private final LegalActRepository actRepository = mock(LegalActRepository.class);
    private final QuestionRepository questionRepository = mock(QuestionRepository.class);
    private final CurrentUserResolver currentUserResolver = mock(CurrentUserResolver.class);
    private final Authentication authentication = mock(Authentication.class);

    private Pool pool = Pools.all();
    /** Przedmioty Studenta: akty każdego przedmiotu z aktami i przedmioty w puli. */
    private StudentCourses studentCourses = new StudentCourses(Map.of(), Set.of(), false);
    private PoolSettingsService service;

    @BeforeEach
    void setUp() {
        service = new PoolSettingsService(Pools.holding(() -> pool, () -> studentCourses),
                poolActRepository, userCourseRepository, courseRepository, trackRepository, fieldRepository,
                actRepository, questionRepository, currentUserResolver);
        User user = new User();
        user.setId(USER);
        when(currentUserResolver.resolve(authentication)).thenReturn(user);
        Track track = new Track();
        track.setId(1L);
        track.setSlug("adwokacka-radcowska");
        track.setName("Aplikacja adwokacka i radcowska");
        track.setShortName("Adwokacka i radcowska");
        track.setKind(TrackKind.EXAM);
        when(trackRepository.findById(1L)).thenReturn(Optional.of(track));
        Track student = new Track();
        student.setId(9L);
        student.setSlug("student");
        student.setName("Student");
        student.setShortName("Student");
        student.setKind(TrackKind.STUDENT);
        when(trackRepository.findById(9L)).thenReturn(Optional.of(student));
        when(fieldRepository.findAll()).thenReturn(List.of(QuestionRefs.field(1), QuestionRefs.field(2)));
        LegalAct a1 = QuestionRefs.act(1, 1);
        LegalAct a2 = QuestionRefs.act(2, 1);
        when(actRepository.findAllWithFieldByIdIn(anyCollection())).thenReturn(List.of(a1, a2));
        when(questionRepository.countByAct(anyCollection(), anyCollection())).thenReturn(List.of(count(1, 30), count(2, 12)));
        when(questionRepository.findRefsInPool(anyCollection(), anyCollection())).thenReturn(List.of(
                QuestionRefs.refOf(QuestionRefs.question(1, 1, 1, 1)),
                QuestionRefs.refOf(QuestionRefs.question(2, 1, 1, 2)),
                QuestionRefs.refOf(QuestionRefs.question(3, 2, 1, 2)),
                QuestionRefs.refOf(QuestionRefs.question(4, 2, 1, 3))));
    }

    private static GroupCount count(long id, long total) {
        return new GroupCount() {
            public Long getGroupId() { return id; }
            public Long getTotal() { return total; }
        };
    }

    // ---------------------------------------------------------------- get

    @Test
    void get_describesTheTrackAndTheActsWithTheirCounts() {
        PoolResponse response = service.get(authentication);

        assertThat(response.track().slug()).isEqualTo("adwokacka-radcowska");
        assertThat(response.narrowed()).isFalse();
        assertThat(response.questionCount()).isEqualTo(4);
        // dziedzina bez aktów w puli (druga) jest pomijana, a ścieżka-aplikacja nie ma przedmiotów
        assertThat(response.fields()).hasSize(1);
        assertThat(response.fields().get(0).acts()).extracting(PoolResponse.ActNode::id).containsExactly(1L, 2L);
        assertThat(response.fields().get(0).acts()).extracting(PoolResponse.ActNode::questionCount).containsExactly(30, 12);
        assertThat(response.fields().get(0).acts()).allMatch(PoolResponse.ActNode::selected);
        assertThat(response.courses()).isEmpty();
    }

    @Test
    void get_marksOnlyTheNarrowedActsAsSelected() {
        pool = Pools.narrowedTo(Pools.all(), 2);

        PoolResponse response = service.get(authentication);

        assertThat(response.narrowed()).isTrue();
        assertThat(response.fields().get(0).acts()).extracting(PoolResponse.ActNode::selected).containsExactly(false, true);
    }

    // ---------------------------------------------------------------- updateActs

    @Test
    void updateActs_storesTheChosenSubset_afterClearingThePreviousOne() {
        service.updateActs(new UpdatePoolActsRequest(List.of(2L)), authentication);

        InOrder order = inOrder(poolActRepository);
        order.verify(poolActRepository).deleteByUserId(USER);
        order.verify(poolActRepository).flush();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<UserPoolAct>> saved = ArgumentCaptor.forClass(Iterable.class);
        order.verify(poolActRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(UserPoolAct::getLegalActId).containsExactly(2L);
        assertThat(saved.getValue()).extracting(UserPoolAct::getUserId).containsOnly(USER);
    }

    @Test
    void updateActs_choosingEveryActOfTheTrack_removesTheNarrowing() {
        // akty dodane później przez administratora mają być widoczne, więc „wszystkie" nie zapisuje się jako lista
        service.updateActs(new UpdatePoolActsRequest(List.of(1L, 2L)), authentication);

        verify(poolActRepository).deleteByUserId(USER);
        verify(poolActRepository, never()).saveAll(any());
    }

    @Test
    void updateActs_anEmptyList_removesTheNarrowing() {
        service.updateActs(new UpdatePoolActsRequest(List.of()), authentication);

        verify(poolActRepository).deleteByUserId(USER);
        verify(poolActRepository, never()).saveAll(any());
    }

    @Test
    void updateActs_aRepeatedActIsStoredOnce() {
        service.updateActs(new UpdatePoolActsRequest(List.of(1L, 1L)), authentication);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<UserPoolAct>> saved = ArgumentCaptor.forClass(Iterable.class);
        verify(poolActRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).hasSize(1);
    }

    @Test
    void updateActs_anActOutsideTheUsersPool_isRejected_andNothingChanges() {
        assertThatThrownBy(() -> service.updateActs(new UpdatePoolActsRequest(List.of(1L, 99L)), authentication))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nie należy do Twojej puli");

        verify(poolActRepository, never()).deleteByUserId(any());
        verify(poolActRepository, never()).saveAll(any());
    }

    // ---------------------------------------------------------------- przedmioty studenta

    private static Course course(long id, String name, int sortOrder) {
        Course course = new Course();
        course.setId(id);
        course.setName(name);
        course.setSlug("przedmiot-" + id);
        course.setSortOrder(sortOrder);
        return course;
    }

    /** Student z przedmiotami: 10 (akt 1), 11 (akt 2), 12 (akty 1 i 2); w puli {@code inPool}. */
    private void asStudent(Set<Long> inPool, boolean narrowed) {
        Map<Long, List<Long>> acts = new LinkedHashMap<>();
        acts.put(10L, List.of(1L));
        acts.put(11L, List.of(2L));
        acts.put(12L, List.of(1L, 2L));
        studentCourses = new StudentCourses(acts, new LinkedHashSet<>(inPool), narrowed);
        pool = Pools.student(1, 2);
        when(courseRepository.findAllByOrderBySortOrderAscIdAsc()).thenReturn(List.of(
                course(11, "Historia prawa", 20), course(10, "Prawo karne", 10), course(12, "Przekrojowy", 30),
                course(13, "Pusty", 40)));
    }

    @Test
    void get_forAStudent_listsTheCoursesWithQuestionCountsAndSelection() {
        asStudent(Set.of(10L), true);

        PoolResponse response = service.get(authentication);

        assertThat(response.track().slug()).isEqualTo("student");
        assertThat(response.courses()).extracting(PoolResponse.CourseNode::id).containsExactly(11L, 10L, 12L);
        // kolejność repozytorium (sort_order ustala baza), liczba pytań to suma aktów przedmiotu ze wszystkich źródeł
        assertThat(response.courses()).extracting(PoolResponse.CourseNode::questionCount).containsExactly(12, 30, 42);
        assertThat(response.courses()).extracting(PoolResponse.CourseNode::selected).containsExactly(false, true, false);
    }

    @Test
    void get_forAStudent_skipsCoursesWithNoQuestions() {
        asStudent(Set.of(10L, 11L, 12L), false);
        when(questionRepository.countByAct(anyCollection(), anyCollection())).thenReturn(List.of(count(1, 30)));

        PoolResponse response = service.get(authentication);

        // przedmiot 11 ma tylko akt 2 (0 pytań), a 13 nie ma aktów
        assertThat(response.courses()).extracting(PoolResponse.CourseNode::id).containsExactly(10L, 12L);
        assertThat(response.courses()).allMatch(PoolResponse.CourseNode::selected);
    }

    @Test
    void updateCourses_storesTheChosenSubset_afterClearingThePreviousOne() {
        asStudent(Set.of(10L, 11L, 12L), false);

        service.updateCourses(new UpdatePoolCoursesRequest(List.of(10L, 12L)), authentication);

        InOrder order = inOrder(userCourseRepository);
        order.verify(userCourseRepository).deleteByUserId(USER);
        order.verify(userCourseRepository).flush();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<UserCourse>> saved = ArgumentCaptor.forClass(Iterable.class);
        order.verify(userCourseRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(UserCourse::getCourseId).containsExactly(10L, 12L);
        assertThat(saved.getValue()).extracting(UserCourse::getUserId).containsOnly(USER);
    }

    @Test
    void updateCourses_choosingEveryCourse_orNone_removesTheChoice() {
        asStudent(Set.of(10L, 11L, 12L), false);

        service.updateCourses(new UpdatePoolCoursesRequest(List.of(10L, 11L, 12L)), authentication);
        service.updateCourses(new UpdatePoolCoursesRequest(List.of()), authentication);

        verify(userCourseRepository, org.mockito.Mockito.times(2)).deleteByUserId(USER);
        verify(userCourseRepository, never()).saveAll(any());
    }

    @Test
    void updateCourses_aRepeatedCourseIsStoredOnce() {
        asStudent(Set.of(10L, 11L, 12L), false);

        service.updateCourses(new UpdatePoolCoursesRequest(java.util.Arrays.asList(10L, 10L, null)), authentication);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<UserCourse>> saved = ArgumentCaptor.forClass(Iterable.class);
        verify(userCourseRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).hasSize(1);
    }

    @Test
    void updateCourses_aCourseThatDoesNotExistOrHasNoActs_isRejected_andNothingChanges() {
        asStudent(Set.of(10L, 11L, 12L), false);

        assertThatThrownBy(() -> service.updateCourses(new UpdatePoolCoursesRequest(List.of(10L, 13L)), authentication))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nie istnieje");

        verify(userCourseRepository, never()).deleteByUserId(any());
        verify(userCourseRepository, never()).saveAll(any());
    }

    @Test
    void updateCourses_isOnlyForTheStudentTrack() {
        // pula z @BeforeEach to ścieżka-aplikacja
        assertThatThrownBy(() -> service.updateCourses(new UpdatePoolCoursesRequest(List.of(10L)), authentication))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("na ścieżce Student");

        verify(userCourseRepository, never()).deleteByUserId(any());
    }
}
