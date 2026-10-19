package pl.tutlaw.backend.settings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.tutlaw.backend.settings.dto.ExamTermResponse;
import pl.tutlaw.backend.settings.dto.UpdateExamTermRequest;
import pl.tutlaw.backend.testsupport.MutableClock;
import pl.tutlaw.backend.track.Track;
import pl.tutlaw.backend.track.TrackKind;
import pl.tutlaw.backend.track.TrackRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamTermServiceTest {

    private final MutableClock clock = MutableClock.at("2026-10-12T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);

    private static final long ADWOKACKA = 1L;
    private static final long SEDZIOWSKA = 2L;
    private static final long UZUPELNIAJACA = 3L;
    private static final long NOTARIALNA = 4L;
    private static final long KOMORNICZA = 5L;
    private static final long STUDENT = 6L;

    @Mock private ExamTermRepository repository;
    @Mock private TrackRepository trackRepository;

    private ExamTermService service;
    private List<Track> tracks;

    @BeforeEach
    void setUp() {
        service = new ExamTermService(repository, trackRepository, clock);
        tracks = List.of(
                track(ADWOKACKA, "adwokacka-radcowska", "Adwokacka i radcowska", TrackKind.EXAM),
                track(SEDZIOWSKA, "sedziowska-prokuratorska", "Sędziowska i prokuratorska", TrackKind.EXAM),
                track(UZUPELNIAJACA, "uzupelniajaca-sedziowska", "Uzupełniająca sędziowska", TrackKind.EXAM),
                track(NOTARIALNA, "notarialna", "Notarialna", TrackKind.EXAM),
                track(KOMORNICZA, "komornicza", "Komornicza", TrackKind.EXAM),
                track(STUDENT, "student", "Student", TrackKind.STUDENT));
    }

    private static Track track(long id, String slug, String name, TrackKind kind) {
        Track track = new Track();
        track.setId(id);
        track.setSlug(slug);
        track.setName(name);
        track.setShortName(name);
        track.setKind(kind);
        return track;
    }

    private static ExamTerm term(long trackId, LocalDate date) {
        ExamTerm term = new ExamTerm(trackId);
        term.setExamDate(date);
        return term;
    }

    private void tracksExist() {
        when(trackRepository.findAllByOrderBySortOrderAscIdAsc()).thenReturn(tracks);
    }

    private void trackExists(long id) {
        when(trackRepository.findById(id)).thenReturn(tracks.stream().filter(t -> t.getId() == id).findFirst());
    }

    private static ExamTermResponse of(List<ExamTermResponse> terms, long trackId) {
        return terms.stream().filter(t -> t.track().id() == trackId).findFirst().orElseThrow();
    }

    // ---------------------------------------------------------------- lista

    @Test
    void list_returnsEveryExamTrackInTrackOrder_withoutTheStudentTrack() {
        tracksExist();
        when(repository.findAll()).thenReturn(List.of());

        List<ExamTermResponse> terms = service.list();

        assertThat(terms).extracting(t -> t.track().slug()).containsExactly(
                "adwokacka-radcowska", "sedziowska-prokuratorska", "uzupelniajaca-sedziowska", "notarialna", "komornicza");
        assertThat(terms).allSatisfy(t -> {
            assertThat(t.examDate()).isNull();
            assertThat(t.daysToExam()).isNull();
        });
    }

    @Test
    void list_showsTheStoredDateAndDaysLeft() {
        tracksExist();
        when(repository.findAll()).thenReturn(List.of(term(NOTARIALNA, TODAY.plusDays(40)), term(KOMORNICZA, null)));

        List<ExamTermResponse> terms = service.list();

        assertThat(of(terms, NOTARIALNA).examDate()).isEqualTo(TODAY.plusDays(40));
        assertThat(of(terms, NOTARIALNA).daysToExam()).isEqualTo(40L);
        assertThat(of(terms, KOMORNICZA).examDate()).isNull();
    }

    @Test
    void list_theSupplementaryJudgeHasItsOwnDate_notTheSameAsTheJudgeProsecutorOne() {
        tracksExist();
        when(repository.findAll()).thenReturn(List.of(
                term(SEDZIOWSKA, TODAY.plusDays(100)), term(UZUPELNIAJACA, TODAY.plusDays(200))));

        List<ExamTermResponse> terms = service.list();

        assertThat(of(terms, SEDZIOWSKA).daysToExam()).isEqualTo(100L);
        assertThat(of(terms, UZUPELNIAJACA).daysToExam()).isEqualTo(200L);
    }

    @Test
    void list_examDay_isZeroDays_andPastDate_hasNoCountdown() {
        tracksExist();
        when(repository.findAll()).thenReturn(List.of(term(NOTARIALNA, TODAY), term(KOMORNICZA, TODAY.minusDays(3))));

        List<ExamTermResponse> terms = service.list();

        assertThat(of(terms, NOTARIALNA).daysToExam()).isEqualTo(0L);
        assertThat(of(terms, KOMORNICZA).examDate()).isEqualTo(TODAY.minusDays(3));
        assertThat(of(terms, KOMORNICZA).daysToExam()).isNull();
    }

    // ---------------------------------------------------------------- zapis

    @Test
    void update_setsTheDateOfAnExistingTerm() {
        trackExists(NOTARIALNA);
        ExamTerm existing = term(NOTARIALNA, null);
        when(repository.findById(NOTARIALNA)).thenReturn(Optional.of(existing));

        ExamTermResponse response = service.update(NOTARIALNA, new UpdateExamTermRequest(TODAY.plusDays(60)));

        ArgumentCaptor<ExamTerm> captor = ArgumentCaptor.forClass(ExamTerm.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
        assertThat(captor.getValue().getExamDate()).isEqualTo(TODAY.plusDays(60));
        assertThat(response.daysToExam()).isEqualTo(60L);
        assertThat(response.track().slug()).isEqualTo("notarialna");
    }

    @Test
    void update_createsTheRowWhenMissing() {
        trackExists(KOMORNICZA);
        when(repository.findById(KOMORNICZA)).thenReturn(Optional.empty());

        service.update(KOMORNICZA, new UpdateExamTermRequest(TODAY.plusDays(10)));

        ArgumentCaptor<ExamTerm> captor = ArgumentCaptor.forClass(ExamTerm.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTrackId()).isEqualTo(KOMORNICZA);
        assertThat(captor.getValue().getExamDate()).isEqualTo(TODAY.plusDays(10));
    }

    @Test
    void update_nullClearsTheDate() {
        trackExists(NOTARIALNA);
        ExamTerm existing = term(NOTARIALNA, TODAY.plusDays(30));
        when(repository.findById(NOTARIALNA)).thenReturn(Optional.of(existing));

        ExamTermResponse response = service.update(NOTARIALNA, new UpdateExamTermRequest(null));

        assertThat(existing.getExamDate()).isNull();
        assertThat(response.examDate()).isNull();
        assertThat(response.daysToExam()).isNull();
    }

    @Test
    void update_todayIsAllowed() {
        trackExists(NOTARIALNA);
        when(repository.findById(NOTARIALNA)).thenReturn(Optional.empty());

        ExamTermResponse response = service.update(NOTARIALNA, new UpdateExamTermRequest(TODAY));

        assertThat(response.daysToExam()).isEqualTo(0L);
    }

    @Test
    void update_rejectsTheStudentTrack() {
        trackExists(STUDENT);

        assertThatThrownBy(() -> service.update(STUDENT, new UpdateExamTermRequest(TODAY.plusDays(5))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Student");

        verify(repository, never()).save(any());
    }

    @Test
    void update_rejectsAnUnknownTrack() {
        when(trackRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, new UpdateExamTermRequest(TODAY.plusDays(5))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nie ma takiej ścieżki");

        verify(repository, never()).save(any());
    }

    @Test
    void update_rejectsPastDate() {
        trackExists(NOTARIALNA);

        assertThatThrownBy(() -> service.update(NOTARIALNA, new UpdateExamTermRequest(TODAY.minusDays(1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("w przeszłości");

        verify(repository, never()).save(any());
    }

    @Test
    void update_rejectsDateMoreThanThreeYearsAway() {
        trackExists(NOTARIALNA);

        assertThatThrownBy(() -> service.update(NOTARIALNA, new UpdateExamTermRequest(TODAY.plusYears(3).plusDays(1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("zbyt odległy");

        verify(repository, never()).save(any());
    }
}
