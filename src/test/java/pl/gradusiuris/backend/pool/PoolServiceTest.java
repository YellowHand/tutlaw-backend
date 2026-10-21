package pl.gradusiuris.backend.pool;

import org.junit.jupiter.api.Test;
import pl.gradusiuris.backend.course.CourseRepository;
import pl.gradusiuris.backend.legal.LegalActRepository;
import pl.gradusiuris.backend.question.QuestionRepository;
import pl.gradusiuris.backend.settings.UserSettings;
import pl.gradusiuris.backend.settings.UserSettingsRepository;
import pl.gradusiuris.backend.source.QuestionSourceRepository;
import pl.gradusiuris.backend.source.SourceKind;
import pl.gradusiuris.backend.testsupport.FakeRepos;
import pl.gradusiuris.backend.track.Track;
import pl.gradusiuris.backend.track.TrackKind;
import pl.gradusiuris.backend.track.TrackRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reguła dostępu do pytań na prawdziwym PoolService, z repozytoriami "w pamięci".
 * <p>Dane: akty 1-6. Ścieżka 1 (adwokacka) ma na liście akty 1 i 2 oraz własny arkusz 1 z pytaniami z aktów 2 i 3;
 * ścieżka 2 (notarialna) ma arkusz 2; źródło 3 to pytania autorskie; ścieżka 9 to Student z przedmiotami:
 * 10 (akt 4), 11 (akt 5) i 12 (akty 4 i 5, więc nakłada się na dwa pozostałe); przedmiot 13 nie ma aktów.
 */
class PoolServiceTest {

    private static final long USER = 7L;

    private static final class Fixture {
        UserSettings settings;
        final List<Long> chosenActs = new ArrayList<>();
        final List<Long> chosenCourses = new ArrayList<>();
        final List<Track> tracks = new ArrayList<>();
        final PoolService service;

        Fixture() {
            tracks.add(track(1L, "adwokacka-radcowska", TrackKind.EXAM));
            tracks.add(track(2L, "notarialna", TrackKind.EXAM));
            tracks.add(track(9L, "student", TrackKind.STUDENT));
            // ścieżka bez aktów i bez arkuszy
            tracks.add(track(5L, "pusta", TrackKind.EXAM));

            UserSettingsRepository settingsRepository = FakeRepos.proxy(UserSettingsRepository.class, (method, args) -> {
                assertThat(method).isEqualTo("findById");
                return Optional.ofNullable(settings);
            });
            TrackRepository trackRepository = FakeRepos.proxy(TrackRepository.class, (method, args) -> switch (method) {
                case "findById" -> tracks.stream().filter(t -> t.getId().equals(args[0])).findFirst();
                case "findActIds" -> args[0].equals(1L) ? List.of(1L, 2L) : List.of();
                default -> throw new AssertionError("nieoczekiwane: " + method);
            });
            CourseRepository courseRepository = FakeRepos.proxy(CourseRepository.class, (method, args) -> {
                assertThat(method).isEqualTo("findCourseActs");
                return List.of(courseAct(10L, 4L), courseAct(11L, 5L), courseAct(12L, 4L), courseAct(12L, 5L));
            });
            UserCourseRepository userCourseRepository = FakeRepos.proxy(UserCourseRepository.class, (method, args) -> {
                assertThat(method).isEqualTo("findCourseIdsByUserId");
                return List.copyOf(chosenCourses);
            });
            LegalActRepository actRepository = FakeRepos.proxy(LegalActRepository.class, (method, args) -> {
                assertThat(method).isEqualTo("findAllIds");
                return List.of(1L, 2L, 3L, 4L, 5L, 6L);
            });
            QuestionSourceRepository sourceRepository = FakeRepos.proxy(QuestionSourceRepository.class, (method, args) -> switch (method) {
                case "findIdsByTrackId" -> args[0].equals(1L) ? List.of(1L) : args[0].equals(2L) ? List.of(2L) : List.of();
                case "findAllRows" -> List.of(row(1L, SourceKind.EXAM_SHEET, 1L), row(2L, SourceKind.EXAM_SHEET, 2L),
                        row(3L, SourceKind.AUTHORED, null));
                default -> throw new AssertionError("nieoczekiwane: " + method);
            });
            QuestionRepository questionRepository = FakeRepos.proxy(QuestionRepository.class, (method, args) -> {
                assertThat(method).isEqualTo("findActIdsInSources");
                // pytania arkusza 1 leżą w aktach 2 i 3, arkusza 2 w akcie 6
                @SuppressWarnings("unchecked")
                java.util.Collection<Long> sources = (java.util.Collection<Long>) args[0];
                List<Long> acts = new ArrayList<>();
                if (sources.contains(1L)) acts.addAll(List.of(2L, 3L));
                if (sources.contains(2L)) acts.add(6L);
                return acts;
            });
            UserPoolActRepository poolActRepository = FakeRepos.proxy(UserPoolActRepository.class, (method, args) -> {
                assertThat(method).isEqualTo("findActIdsByUserId");
                return List.copyOf(chosenActs);
            });
            service = new PoolService(settingsRepository, trackRepository, courseRepository, actRepository,
                    sourceRepository, questionRepository, poolActRepository, userCourseRepository);
        }

        private static CourseRepository.CourseAct courseAct(Long courseId, Long actId) {
            return new CourseRepository.CourseAct() {
                public Long getCourseId() { return courseId; }
                public Long getActId() { return actId; }
            };
        }

        Fixture onTrack(Long trackId) {
            settings = new UserSettings(USER);
            settings.setTrackId(trackId);
            return this;
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

        private static QuestionSourceRepository.SourceRow row(Long id, SourceKind kind, Long trackId) {
            return new QuestionSourceRepository.SourceRow() {
                public Long getId() { return id; }
                public String getSlug() { return "zrodlo-" + id; }
                public SourceKind getKind() { return kind; }
                public String getName() { return "Źródło " + id; }
                public Long getTrackId() { return trackId; }
                public Integer getExamYear() { return kind == SourceKind.AUTHORED ? null : 2020 + id.intValue(); }
            };
        }
    }

    @Test
    void aUserWhoPickedNoTrack_seesEverything() {
        Pool pool = new Fixture().service.forUser(USER);

        assertThat(pool.trackId()).isNull();
        assertThat(pool.trackKind()).isNull();
        assertThat(pool.baseActIds()).containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
        assertThat(pool.actIds()).containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
        assertThat(pool.narrowed()).isFalse();
        assertThat(pool.ownSheetIds()).isEmpty();
        assertThat(pool.sourceIds()).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void anExamTrack_isItsOwnActsPlusTheActsOfItsOwnSheets() {
        Pool pool = new Fixture().onTrack(1L).service.forUser(USER);

        assertThat(pool.trackKind()).isEqualTo(TrackKind.EXAM);
        // 1 i 2 z listy administratora, 2 i 3 z pytań własnego arkusza; akt 6 (arkusz innej ścieżki) nie wchodzi
        assertThat(pool.baseActIds()).containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(pool.actIds()).containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(pool.ownSheetIds()).containsExactly(1L);
    }

    @Test
    void theStudentTrack_isTheActsOfAllCourses_andHasNoOwnSheets() {
        Pool pool = new Fixture().onTrack(9L).service.forUser(USER);

        assertThat(pool.isStudent()).isTrue();
        assertThat(pool.baseActIds()).containsExactlyInAnyOrder(4L, 5L);
        assertThat(pool.ownSheetIds()).isEmpty();
    }

    @Test
    void aStudentWhoChoseNoCourses_hasAllCourses_andIsNotNarrowed() {
        Pool pool = new Fixture().onTrack(9L).service.forUser(USER);

        assertThat(pool.narrowed()).isFalse();
        assertThat(pool.actIds()).containsExactlyInAnyOrder(4L, 5L);
    }

    @Test
    void aStudentWhoChoseOneCourse_getsOnlyTheActsOfThatCourse() {
        Fixture f = new Fixture().onTrack(9L);
        f.chosenCourses.add(10L);

        Pool pool = f.service.forUser(USER);

        assertThat(pool.narrowed()).isTrue();
        assertThat(pool.baseActIds()).containsExactly(4L);
        assertThat(pool.actIds()).containsExactly(4L);
    }

    @Test
    void overlappingCourses_shareTheirActs_withoutDuplicates() {
        Fixture f = new Fixture().onTrack(9L);
        f.chosenCourses.addAll(List.of(10L, 12L)); // akt 4 jest w obu przedmiotach

        Pool pool = f.service.forUser(USER);

        assertThat(pool.narrowed()).isTrue();
        assertThat(pool.actIds()).containsExactlyInAnyOrder(4L, 5L);
        assertThat(pool.actIds()).doesNotHaveDuplicates();
    }

    @Test
    void aStudentWhoChoseEveryCourse_isNotNarrowed() {
        Fixture f = new Fixture().onTrack(9L);
        f.chosenCourses.addAll(List.of(10L, 11L, 12L));

        assertThat(f.service.forUser(USER).narrowed()).isFalse();
    }

    @Test
    void aChosenCourseThatNoLongerExistsOrHasNoActs_doesNotCutTheWholePool() {
        Fixture f = new Fixture().onTrack(9L);
        f.chosenCourses.addAll(List.of(13L, 99L)); // 13 nie ma aktów, 99 skasowano

        Pool pool = f.service.forUser(USER);

        assertThat(pool.narrowed()).isFalse();
        assertThat(pool.actIds()).containsExactlyInAnyOrder(4L, 5L);
    }

    @Test
    void aStudentNarrowsWithCourses_notWithActs() {
        Fixture f = new Fixture().onTrack(9L);
        f.chosenActs.add(4L); // zawężenie do aktów z dawnej ścieżki nie obowiązuje Studenta

        Pool pool = f.service.forUser(USER);

        assertThat(pool.narrowed()).isFalse();
        assertThat(pool.actIds()).containsExactlyInAnyOrder(4L, 5L);
    }

    @Test
    void chosenCourses_areIgnoredOnAnExamTrack_butKeptForTheWayBack() {
        Fixture f = new Fixture().onTrack(9L);
        f.chosenCourses.add(10L);
        assertThat(f.service.forUser(USER).actIds()).containsExactly(4L);

        f.settings.setTrackId(1L);
        assertThat(f.service.forUser(USER).actIds()).containsExactlyInAnyOrder(1L, 2L, 3L);

        f.settings.setTrackId(9L);
        assertThat(f.service.forUser(USER).actIds()).containsExactly(4L);
    }

    @Test
    void studentCourses_describeTheActsOfEveryCourseWithActs() {
        Fixture f = new Fixture();
        f.chosenCourses.add(11L);

        StudentCourses courses = f.service.studentCourses(USER);

        assertThat(courses.actsByCourse()).containsOnlyKeys(10L, 11L, 12L);
        assertThat(courses.actsByCourse().get(12L)).containsExactly(4L, 5L);
        assertThat(courses.inPool()).containsExactly(11L);
        assertThat(courses.narrowed()).isTrue();
    }

    @Test
    void aTrackWithNothingInIt_getsTheSentinelInsteadOfAnEmptyList() {
        Pool pool = new Fixture().onTrack(5L).service.forUser(USER);

        assertThat(pool.baseActIds()).containsExactly(Pool.NO_SUCH_ID);
        assertThat(pool.actIds()).containsExactly(Pool.NO_SUCH_ID);
        assertThat(pool.ownSheetIds()).isEmpty();
    }

    @Test
    void aDeletedTrack_isTreatedAsNoTrack() {
        Pool pool = new Fixture().onTrack(404L).service.forUser(USER);

        assertThat(pool.trackId()).isNull();
        assertThat(pool.baseActIds()).hasSize(6);
    }

    @Test
    void sourceMode_splitsTheSourcesIntoMinistryAndAuthored() {
        Fixture f = new Fixture().onTrack(1L);

        // bez wyboru źródła: wszystkie
        assertThat(f.service.forUser(USER).sourceIds()).containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(f.service.forUser(USER, SourceMode.ALL.filter()).sourceIds()).containsExactlyInAnyOrder(1L, 2L, 3L);

        // Ministerstwo Sprawiedliwości: własne arkusze ścieżki i arkusze innych ścieżek
        assertThat(f.service.forUser(USER, SourceMode.MINISTRY.filter()).sourceIds()).containsExactlyInAnyOrder(1L, 2L);

        Pool authoredOnly = f.service.forUser(USER, SourceMode.AUTHORED.filter());
        assertThat(authoredOnly.sourceIds()).containsExactly(3L);
        // wszystkie źródła zostają znane (fiszki i egzamin nie stosują filtra)
        assertThat(authoredOnly.allSourceIds()).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void theStoredSourceColumns_areIgnored_theSourceIsChosenPerTest() {
        Fixture f = new Fixture().onTrack(1L);

        // kolumny include_* zostały w bazie, ale encja ich już nie zna: pula bez wyboru źródła ma zawsze wszystkie
        assertThat(f.service.forUser(USER).filter()).isEqualTo(SourceFilter.ALL);
        assertThat(f.service.forUser(USER, SourceMode.AUTHORED.filter()).filter()).isEqualTo(SourceMode.AUTHORED.filter());
    }

    @Test
    void forStudent_everySheetCountsAsMinistry() {
        Fixture f = new Fixture().onTrack(9L);

        // Student nie ma „własnych arkuszy": wszystkie arkusze to arkusze Ministerstwa
        assertThat(f.service.forUser(USER, SourceMode.MINISTRY.filter()).sourceIds()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(f.service.forUser(USER, SourceMode.AUTHORED.filter()).sourceIds()).containsExactly(3L);
    }

    @Test
    void narrowing_keepsOnlyTheChosenActsThatAreInThePool() {
        Fixture f = new Fixture().onTrack(1L);
        f.chosenActs.addAll(List.of(2L, 6L)); // 6 nie należy do puli tej ścieżki

        Pool pool = f.service.forUser(USER);

        assertThat(pool.narrowed()).isTrue();
        assertThat(pool.actIds()).containsExactly(2L);
        assertThat(pool.baseActIds()).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void narrowing_thatNothingSurvives_doesNotCutTheWholePool() {
        Fixture f = new Fixture().onTrack(1L);
        f.chosenActs.add(6L); // administrator usunął akt z listy ścieżki

        Pool pool = f.service.forUser(USER);

        assertThat(pool.narrowed()).isFalse();
        assertThat(pool.actIds()).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void theNarrowingIsDroppedWhenTheUserSwitchesToAnotherTrack() {
        Fixture f = new Fixture().onTrack(1L);
        f.chosenActs.add(2L);
        assertThat(f.service.forUser(USER).narrowed()).isTrue();

        f.settings.setTrackId(9L); // Student: akt 2 nie należy do jego puli
        Pool student = f.service.forUser(USER);

        assertThat(student.narrowed()).isFalse();
        assertThat(student.actIds()).containsExactlyInAnyOrder(4L, 5L);
    }

    @Test
    void flashcardsIgnoreTheSourceFilter_andTheExamIgnoresTheNarrowing() {
        Fixture f = new Fixture().onTrack(1L);
        f.chosenActs.add(2L);

        Pool pool = f.service.forUser(USER, new SourceFilter(true, false, false));
        assertThat(pool.sourceIds()).containsExactly(1L);
        assertThat(pool.actIds()).containsExactly(2L);

        assertThat(pool.forFlashcards().sourceIds()).containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(pool.forFlashcards().actIds()).containsExactly(2L); // zawężenie aktów obowiązuje także fiszki

        Pool exam = pool.unnarrowed();
        assertThat(exam.actIds()).containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(exam.narrowed()).isFalse();
        assertThat(exam.sourceIds()).containsExactly(1L);
    }

    @Test
    void forTrack_buildsAPreviewForAnotherTrackWithoutTouchingTheSettings() {
        Fixture f = new Fixture().onTrack(1L);

        Pool preview = f.service.forTrack(USER, f.tracks.get(1), SourceFilter.ALL);

        assertThat(preview.trackId()).isEqualTo(2L);
        // ścieżka 2 nie ma aktów na liście, ale jej arkusz 2 ma pytania z aktu 6
        assertThat(preview.baseActIds()).containsExactly(6L);
        assertThat(preview.ownSheetIds()).containsExactly(2L);
        assertThat(f.settings.getTrackId()).isEqualTo(1L);
    }
}
