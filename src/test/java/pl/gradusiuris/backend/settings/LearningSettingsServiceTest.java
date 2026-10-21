package pl.gradusiuris.backend.settings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import pl.gradusiuris.backend.pool.Pool;
import pl.gradusiuris.backend.pool.PoolService;
import pl.gradusiuris.backend.pool.SourceFilter;
import pl.gradusiuris.backend.pool.UserPoolActRepository;
import pl.gradusiuris.backend.question.Question;
import pl.gradusiuris.backend.question.QuestionRepository;
import pl.gradusiuris.backend.review.ReviewLogRepository;
import pl.gradusiuris.backend.review.ReviewMode;
import pl.gradusiuris.backend.review.UserQuestionStateRepository;
import pl.gradusiuris.backend.settings.dto.ChooseThemeRequest;
import pl.gradusiuris.backend.settings.dto.ChooseTrackRequest;
import pl.gradusiuris.backend.settings.dto.LearningSettingsResponse;
import pl.gradusiuris.backend.settings.dto.UpdateLearningSettingsRequest;
import pl.gradusiuris.backend.testsupport.MutableClock;
import pl.gradusiuris.backend.testsupport.Pools;
import pl.gradusiuris.backend.testsupport.QuestionRefs;
import pl.gradusiuris.backend.track.Track;
import pl.gradusiuris.backend.track.TrackKind;
import pl.gradusiuris.backend.track.TrackRepository;
import pl.gradusiuris.backend.user.CurrentUserResolver;
import pl.gradusiuris.backend.user.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningSettingsServiceTest {

    // 2026-10-12 12:00 czasu warszawskiego
    private final MutableClock clock = MutableClock.at("2026-10-12T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);

    private static final long NOTARIALNA = 4L;
    private static final long KOMORNICZA = 5L;
    private static final long STUDENT = 6L;

    @Mock private UserSettingsRepository repository;
    @Mock private QuestionRepository questionRepository;
    @Mock private UserQuestionStateRepository stateRepository;
    @Mock private ExamTermRepository examTerms;
    @Mock private ReviewLogRepository reviewLogs;
    @Mock private TrackRepository trackRepository;
    @Mock private UserPoolActRepository poolActRepository;
    @Mock private CurrentUserResolver currentUserResolver;
    private final Authentication authentication = mock(Authentication.class);

    private Pool pool = Pools.all();
    private LearningSettingsService service;

    @BeforeEach
    void setUp() {
        LearningDefaults defaults = new LearningDefaults(20, 40, 200, 0.9);
        PoolService poolService = Pools.holding(() -> pool);
        service = new LearningSettingsService(repository, defaults, currentUserResolver, questionRepository,
                stateRepository, examTerms, reviewLogs, trackRepository, poolService, poolActRepository, clock);
    }

    private User user() {
        User user = new User();
        user.setId(5L);
        return user;
    }

    private void loggedIn() {
        when(currentUserResolver.resolve(authentication)).thenReturn(user());
    }

    private static UpdateLearningSettingsRequest request(Integer flashcards, Integer tests, Integer reviews, Double retention) {
        return new UpdateLearningSettingsRequest(flashcards, tests, reviews, retention);
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

    private void trackExists(long id, String slug, String name, TrackKind kind) {
        when(trackRepository.findById(id)).thenReturn(Optional.of(track(id, slug, name, kind)));
    }

    /** Użytkownik z wybraną aplikacją notarialną (bez żadnych stubów, dla testów wołających {@code resolve} wprost). */
    private UserSettings chosen() {
        UserSettings stored = new UserSettings(5L);
        stored.setTrackId(NOTARIALNA);
        return stored;
    }

    /** Użytkownik z wybraną aplikacją, której termin to {@code examDate} (null = termin nieogłoszony). */
    private UserSettings withExam(LocalDate examDate) {
        UserSettings stored = chosen();
        ExamTerm term = new ExamTerm(NOTARIALNA);
        term.setExamDate(examDate);
        when(examTerms.findById(NOTARIALNA)).thenReturn(Optional.of(term));
        return stored;
    }

    // ---------------------------------------------------------------- wartości domyślne

    @Test
    void effective_withoutStoredSettings_usesDefaults() {
        when(repository.findById(5L)).thenReturn(Optional.empty());

        EffectiveSettings e = service.effectiveFor(5L);

        assertThat(e.newFlashcardsPerDay()).isEqualTo(20);
        assertThat(e.newTestQuestionsPerDay()).isEqualTo(40);
        assertThat(e.maxReviewsPerDay()).isEqualTo(200);
        assertThat(e.desiredRetention()).isEqualTo(0.9);
        assertThat(e.effectiveRetention()).isEqualTo(0.9);
        assertThat(e.trackId()).isNull();
        assertThat(e.examDate()).isNull();
        assertThat(e.daysToExam()).isNull();
        assertThat(e.retentionBoosted()).isFalse();
        assertThat(e.newTestQuestionsAuto()).isTrue(); // bez własnego limitu obowiązuje automatyczny
    }

    @Test
    void effective_storedValuesOverrideDefaults_nullFieldsFallBack() {
        UserSettings stored = new UserSettings(5L);
        stored.setNewFlashcardsPerDay(5);
        stored.setMaxReviewsPerDay(80);
        when(repository.findById(5L)).thenReturn(Optional.of(stored));

        EffectiveSettings e = service.effectiveFor(5L);

        assertThat(e.newFlashcardsPerDay()).isEqualTo(5);
        assertThat(e.newTestQuestionsPerDay()).isEqualTo(40);
        assertThat(e.maxReviewsPerDay()).isEqualTo(80);
        assertThat(e.newTestQuestionsAuto()).isTrue();
    }

    @Test
    void effective_ownTestQuestionLimitIsManual() {
        UserSettings stored = new UserSettings(5L);
        stored.setNewTestQuestionsPerDay(25);
        when(repository.findById(5L)).thenReturn(Optional.of(stored));

        EffectiveSettings e = service.effectiveFor(5L);

        assertThat(e.newTestQuestionsPerDay()).isEqualTo(25);
        assertThat(e.newTestQuestionsAuto()).isFalse();
    }

    // ---------------------------------------------------------------- tryb egzaminacyjny (termin z wybranej aplikacji)

    @Test
    void effective_examInTwoWeeks_raisesRetention() {
        EffectiveSettings e = service.resolve(chosen(), TODAY.plusDays(14), TODAY);

        assertThat(e.trackId()).isEqualTo(NOTARIALNA);
        assertThat(e.daysToExam()).isEqualTo(14L);
        assertThat(e.desiredRetention()).isEqualTo(0.9);
        assertThat(e.effectiveRetention()).isEqualTo(0.95);
        assertThat(e.retentionBoosted()).isTrue();
        assertThat(e.examPassed()).isFalse();
    }

    @Test
    void effective_examFarAway_doesNotRaiseRetention() {
        EffectiveSettings e = service.resolve(chosen(), TODAY.plusDays(120), TODAY);

        assertThat(e.daysToExam()).isEqualTo(120L);
        assertThat(e.retentionBoosted()).isFalse();
    }

    @Test
    void effective_examOnTheDay_countsAsZeroDays() {
        assertThat(service.resolve(chosen(), TODAY, TODAY).daysToExam()).isEqualTo(0L);
    }

    @Test
    void effective_examAlreadyPassed_switchesTheExamModeOff() {
        EffectiveSettings e = service.resolve(chosen(), TODAY.minusDays(1), TODAY);

        assertThat(e.daysToExam()).isNull();
        assertThat(e.examPassed()).isTrue();
        assertThat(e.effectiveRetention()).isEqualTo(0.9);
    }

    @Test
    void effective_chosenApplicationWithoutAnnouncedTerm_hasNoExamDate() {
        UserSettings stored = withExam(null);
        when(repository.findById(5L)).thenReturn(Optional.of(stored));

        EffectiveSettings e = service.effectiveFor(5L);

        assertThat(e.trackId()).isEqualTo(NOTARIALNA);
        assertThat(e.examDate()).isNull();
        assertThat(e.daysToExam()).isNull();
        assertThat(e.examPassed()).isFalse();
    }

    @Test
    void effective_readsTheTermOfTheChosenApplication() {
        UserSettings stored = withExam(TODAY.plusDays(40));
        when(repository.findById(5L)).thenReturn(Optional.of(stored));

        EffectiveSettings e = service.effectiveFor(5L);

        assertThat(e.examDate()).isEqualTo(TODAY.plusDays(40));
        assertThat(e.daysToExam()).isEqualTo(40L);
    }

    @Test
    void effective_theStudentTrackHasNoTerm_soNoExamModeEver() {
        UserSettings stored = new UserSettings(5L);
        stored.setTrackId(STUDENT);
        when(repository.findById(5L)).thenReturn(Optional.of(stored));
        when(examTerms.findById(STUDENT)).thenReturn(Optional.empty());

        EffectiveSettings e = service.effectiveFor(5L);

        assertThat(e.trackId()).isEqualTo(STUDENT);
        assertThat(e.examDate()).isNull();
        assertThat(e.daysToExam()).isNull();
        assertThat(e.retentionBoosted()).isFalse();
    }

    @Test
    void effective_withoutChosenTrack_doesNotLookUpTerms() {
        when(repository.findById(5L)).thenReturn(Optional.of(new UserSettings(5L)));

        EffectiveSettings e = service.effectiveFor(5L);

        assertThat(e.trackId()).isNull();
        assertThat(e.examDate()).isNull();
        verify(examTerms, never()).findById(any());
    }

    // ---------------------------------------------------------------- zapis

    @Test
    void update_storesAllFieldsAndReturnsThem() {
        loggedIn();
        when(repository.findById(5L)).thenReturn(Optional.empty());

        LearningSettingsResponse response = service.update(request(10, 60, 150, 0.85), authentication);

        ArgumentCaptor<UserSettings> captor = ArgumentCaptor.forClass(UserSettings.class);
        verify(repository).save(captor.capture());
        UserSettings saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(5L);
        assertThat(saved.getNewFlashcardsPerDay()).isEqualTo(10);
        assertThat(saved.getNewTestQuestionsPerDay()).isEqualTo(60);
        assertThat(saved.getMaxReviewsPerDay()).isEqualTo(150);
        assertThat(saved.getDesiredRetention()).isEqualTo(0.85);
        assertThat(saved.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 12, 12, 0));

        assertThat(response.maxReviewsPerDay()).isEqualTo(150);
        assertThat(response.track()).isNull();
        assertThat(response.daysToExam()).isNull();
        assertThat(response.defaults().maxReviewsPerDay()).isEqualTo(200);
    }

    @Test
    void update_nullsMeanDefaults() {
        UserSettings existing = new UserSettings(5L);
        existing.setNewFlashcardsPerDay(3);
        loggedIn();
        when(repository.findById(5L)).thenReturn(Optional.of(existing));

        LearningSettingsResponse response = service.update(request(null, null, null, null), authentication);

        assertThat(existing.getNewFlashcardsPerDay()).isNull();
        assertThat(response.newFlashcardsPerDay()).isEqualTo(20);
        assertThat(response.examDate()).isNull();
        assertThat(response.plan()).isNull();
    }

    @Test
    void update_doesNotChangeTheChosenTrackNorTheNarrowing() {
        UserSettings existing = withExam(TODAY.plusDays(90));
        loggedIn();
        when(repository.findById(5L)).thenReturn(Optional.of(existing));
        trackExists(NOTARIALNA, "notarialna", "Notarialna", TrackKind.EXAM);
        givenQuestionsInPool(0);
        when(stateRepository.findScheduleByUserIdAndMode(5L, ReviewMode.TEST)).thenReturn(List.of());

        LearningSettingsResponse response = service.update(request(null, 50, null, null), authentication);

        assertThat(existing.getTrackId()).isEqualTo(NOTARIALNA);
        verify(poolActRepository, never()).deleteByUserId(any());
        assertThat(response.track().slug()).isEqualTo("notarialna");
        assertThat(response.daysToExam()).isEqualTo(90L);
    }

    @Test
    void update_rejectsRetentionOutsideTheAllowedRange() {
        loggedIn();

        assertThatThrownBy(() -> service.update(request(null, null, null, 0.5), authentication))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("retencja");
        assertThatThrownBy(() -> service.update(request(null, null, null, 0.99), authentication))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.update(request(null, null, null, Double.NaN), authentication))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    // ---------------------------------------------------------------- wybór ścieżki

    @Test
    void chooseTrack_storesTheTrack_andReturnsItsTerm() {
        loggedIn();
        when(repository.findById(5L)).thenReturn(Optional.empty());
        trackExists(KOMORNICZA, "komornicza", "Komornicza", TrackKind.EXAM);
        ExamTerm term = new ExamTerm(KOMORNICZA);
        term.setExamDate(TODAY.plusDays(100));
        when(examTerms.findById(KOMORNICZA)).thenReturn(Optional.of(term));
        givenQuestionsInPool(0);
        when(stateRepository.findScheduleByUserIdAndMode(5L, ReviewMode.TEST)).thenReturn(List.of());

        LearningSettingsResponse response = service.chooseTrack(new ChooseTrackRequest(KOMORNICZA), authentication);

        ArgumentCaptor<UserSettings> captor = ArgumentCaptor.forClass(UserSettings.class);
        verify(repository).save(captor.capture());
        UserSettings saved = captor.getValue();
        assertThat(saved.getTrackId()).isEqualTo(KOMORNICZA);
        assertThat(saved.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 12, 12, 0));

        assertThat(response.track().id()).isEqualTo(KOMORNICZA);
        assertThat(response.track().kind()).isEqualTo(TrackKind.EXAM);
        assertThat(response.examDate()).isEqualTo(TODAY.plusDays(100));
        assertThat(response.daysToExam()).isEqualTo(100L);
    }

    @Test
    void chooseTrack_changingTheTrack_clearsTheNarrowing() {
        UserSettings existing = new UserSettings(5L);
        existing.setTrackId(NOTARIALNA);
        loggedIn();
        when(repository.findById(5L)).thenReturn(Optional.of(existing));
        trackExists(KOMORNICZA, "komornicza", "Komornicza", TrackKind.EXAM);
        when(examTerms.findById(KOMORNICZA)).thenReturn(Optional.empty());

        LearningSettingsResponse response = service.chooseTrack(new ChooseTrackRequest(KOMORNICZA), authentication);

        assertThat(existing.getTrackId()).isEqualTo(KOMORNICZA);
        verify(poolActRepository).deleteByUserId(5L);
        assertThat(response.examDate()).isNull(); // termin nieogłoszony
        assertThat(response.plan()).isNull();
    }

    @Test
    void chooseTrack_neverTouchesTheLearningProgress() {
        // „Twoje postępy zostają": stany nauki i historia odpowiedzi są per pytanie, a nie per ścieżka
        UserSettings existing = new UserSettings(5L);
        existing.setTrackId(NOTARIALNA);
        loggedIn();
        when(repository.findById(5L)).thenReturn(Optional.of(existing));
        // dwie ścieżki w jednym teście: stub po dowolnym id, bo ścisłe stuby nie lubią różnych argumentów
        when(trackRepository.findById(ArgumentMatchers.anyLong())).thenAnswer(call -> Optional.of(
                (Long) call.getArgument(0) == STUDENT
                        ? track(STUDENT, "student", "Student", TrackKind.STUDENT)
                        : track(NOTARIALNA, "notarialna", "Notarialna", TrackKind.EXAM)));

        service.chooseTrack(new ChooseTrackRequest(STUDENT), authentication);
        service.chooseTrack(new ChooseTrackRequest(NOTARIALNA), authentication);

        for (Object repo : List.of(stateRepository, reviewLogs)) {
            assertThat(mockingDetails(repo).getInvocations())
                    .extracting(invocation -> invocation.getMethod().getName())
                    .noneMatch(name -> name.startsWith("delete") || name.startsWith("save") || name.startsWith("remove"));
        }
    }

    @Test
    void chooseTrack_chosenAgain_changesNothing() {
        UserSettings existing = new UserSettings(5L);
        existing.setTrackId(NOTARIALNA);
        existing.setNewTestQuestionsPerDay(77);
        loggedIn();
        when(repository.findById(5L)).thenReturn(Optional.of(existing));
        trackExists(NOTARIALNA, "notarialna", "Notarialna", TrackKind.EXAM);
        when(examTerms.findById(NOTARIALNA)).thenReturn(Optional.empty());

        service.chooseTrack(new ChooseTrackRequest(NOTARIALNA), authentication);

        assertThat(existing.getNewTestQuestionsPerDay()).isEqualTo(77);
        verify(poolActRepository, never()).deleteByUserId(any());
    }

    @Test
    void chooseTrack_keepsOtherSettings() {
        UserSettings existing = new UserSettings(5L);
        existing.setNewTestQuestionsPerDay(77);
        existing.setNewFlashcardsPerDay(9);
        existing.setTheme(Theme.LIGHT);
        loggedIn();
        when(repository.findById(5L)).thenReturn(Optional.of(existing));
        trackExists(NOTARIALNA, "notarialna", "Notarialna", TrackKind.EXAM);
        when(examTerms.findById(NOTARIALNA)).thenReturn(Optional.empty());

        service.chooseTrack(new ChooseTrackRequest(NOTARIALNA), authentication);

        assertThat(existing.getNewTestQuestionsPerDay()).isEqualTo(77);
        assertThat(existing.getNewFlashcardsPerDay()).isEqualTo(9);
        assertThat(existing.getTheme()).isEqualTo(Theme.LIGHT);
    }

    @Test
    void chooseTrack_student_hasNoExamDateAndNoPlan() {
        UserSettings existing = new UserSettings(5L);
        existing.setTrackId(NOTARIALNA);
        loggedIn();
        when(repository.findById(5L)).thenReturn(Optional.of(existing));
        trackExists(STUDENT, "student", "Student", TrackKind.STUDENT);
        when(examTerms.findById(STUDENT)).thenReturn(Optional.empty());

        LearningSettingsResponse response = service.chooseTrack(new ChooseTrackRequest(STUDENT), authentication);

        assertThat(existing.getTrackId()).isEqualTo(STUDENT);
        assertThat(response.track().kind()).isEqualTo(TrackKind.STUDENT);
        assertThat(response.examDate()).isNull();
        assertThat(response.daysToExam()).isNull();
        assertThat(response.plan()).isNull();
        verify(poolActRepository).deleteByUserId(5L);
    }

    @Test
    void chooseTrack_studentCanChooseAnApplicationLater() {
        UserSettings student = new UserSettings(5L);
        student.setTrackId(STUDENT);
        loggedIn();
        when(repository.findById(5L)).thenReturn(Optional.of(student));
        trackExists(NOTARIALNA, "notarialna", "Notarialna", TrackKind.EXAM);
        when(examTerms.findById(NOTARIALNA)).thenReturn(Optional.empty());

        LearningSettingsResponse response = service.chooseTrack(new ChooseTrackRequest(NOTARIALNA), authentication);

        assertThat(response.track().slug()).isEqualTo("notarialna");
        assertThat(student.getTrackId()).isEqualTo(NOTARIALNA);
    }

    @Test
    void chooseTrack_rejectsAnUnknownTrack() {
        loggedIn();
        when(trackRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.chooseTrack(new ChooseTrackRequest(99L), authentication))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Nie ma takiej ścieżki");
        verify(repository, never()).save(any());
        verify(poolActRepository, never()).deleteByUserId(any());
    }

    @Test
    void get_withoutAChosenTrack_reportsNone_soTheAppShowsTheStartDialog() {
        loggedIn();
        when(repository.findById(5L)).thenReturn(Optional.empty());
        assertThat(service.get(authentication).track()).isNull();

        when(repository.findById(5L)).thenReturn(Optional.of(new UserSettings(5L))); // wiersz jest, ścieżki nie wybrano
        assertThat(service.get(authentication).track()).isNull();
    }

    // ---------------------------------------------------------------- plan

    private static UserQuestionStateRepository.QuestionDue started(long questionId) {
        return new UserQuestionStateRepository.QuestionDue() {
            public Long getQuestionId() { return questionId; }
            public LocalDateTime getDueDate() { return LocalDateTime.of(2026, 10, 20, 0, 0); }
            public Double getStability() { return 3.0; }
            public LocalDateTime getLastReviewAt() { return LocalDateTime.of(2026, 10, 10, 9, 0); }
            public Integer getLapses() { return 0; }
            public Boolean getSuspended() { return false; }
        };
    }

    private void startedToday(long count) {
        when(reviewLogs.countFirstReviewsBetween(
                eq(5L), eq(ReviewMode.TEST), eq(LocalDateTime.of(2026, 10, 12, 0, 0)), eq(LocalDateTime.of(2026, 10, 13, 0, 0))))
                .thenReturn(count);
    }

    /** Pytania 1..count w puli: odpowiadają zapytaniu o pytania puli (akty i dozwolone źródła z bieżącej puli). */
    private void givenQuestionsInPool(int count) {
        List<Question> all = new ArrayList<>();
        for (long id = 1; id <= count; id++) {
            all.add(QuestionRefs.question(id));
        }
        givenQuestions(all);
    }

    private void givenQuestions(List<Question> all) {
        when(questionRepository.findRefsInPool(ArgumentMatchers.<Collection<Long>>any(), ArgumentMatchers.<Collection<Long>>any()))
                .thenAnswer(inv -> QuestionRefs.inPool(all, inv.getArgument(0), inv.getArgument(1)));
    }

    private void startedTests(long from, long to) {
        List<UserQuestionStateRepository.QuestionDue> schedule = new ArrayList<>();
        for (long id = from; id <= to; id++) schedule.add(started(id));
        when(stateRepository.findScheduleByUserIdAndMode(5L, ReviewMode.TEST)).thenReturn(schedule);
    }

    @Test
    void get_withExamDate_buildsPlanFromUntouchedQuestionsOnly() {
        UserSettings stored = withExam(TODAY.plusDays(44)); // 44 - 14 rezerwy = 30 dni planowania
        loggedIn();
        when(repository.findById(5L)).thenReturn(Optional.of(stored));
        givenQuestionsInPool(70);
        startedTests(1, 10); // użytkownik ruszył już 10 pytań w teście
        startedToday(0);

        LearningSettingsResponse.Plan plan = service.get(authentication).plan();

        assertThat(plan.newTestQuestionsLeft()).isEqualTo(60);
        assertThat(plan.planningDays()).isEqualTo(30);
        assertThat(plan.suggestedTestQuestionsPerDay()).isEqualTo(2);
        assertThat(plan.newTestQuestionsToday()).isEqualTo(0);
    }

    @Test
    void get_todaysTargetIsComputedFromTheStartOfTheDay_soItDoesNotShrinkWhileLearning() {
        UserSettings stored = withExam(TODAY.plusDays(44)); // 30 dni planowania
        loggedIn();
        when(repository.findById(5L)).thenReturn(Optional.of(stored));
        givenQuestionsInPool(300);
        startedTests(1, 20); // 300 pytań, dziś zaczęto 20: zostało 280, a cel dnia nadal wynika z 300 / 30 = 10
        startedToday(20);

        LearningSettingsResponse.Plan plan = service.get(authentication).plan();

        assertThat(plan.newTestQuestionsLeft()).isEqualTo(280);
        assertThat(plan.suggestedTestQuestionsPerDay()).isEqualTo(10);
        assertThat(plan.newTestQuestionsToday()).isEqualTo(20);
    }

    @Test
    void get_inTheLastTwoWeeks_planIsReviewOnly() {
        UserSettings stored = withExam(TODAY.plusDays(14));
        loggedIn();
        when(repository.findById(5L)).thenReturn(Optional.of(stored));
        givenQuestionsInPool(50);
        when(stateRepository.findScheduleByUserIdAndMode(5L, ReviewMode.TEST)).thenReturn(List.of());
        startedToday(0);

        LearningSettingsResponse.Plan plan = service.get(authentication).plan();

        assertThat(plan.newTestQuestionsLeft()).isEqualTo(50);
        assertThat(plan.planningDays()).isEqualTo(0);
        assertThat(plan.suggestedTestQuestionsPerDay()).isEqualTo(0); // nie "50 dziennie": zostają same powtórki
    }

    @Test
    void get_withoutChosenTrack_hasNoPlan_andDoesNotLoadQuestions() {
        loggedIn();
        when(repository.findById(5L)).thenReturn(Optional.empty());

        LearningSettingsResponse response = service.get(authentication);

        assertThat(response.plan()).isNull();
        assertThat(response.track()).isNull();
        verify(questionRepository, never()).findRefsInPool(any(), any());
    }

    @Test
    void get_applicationWithoutAnnouncedTerm_hasNoPlan() {
        loggedIn();
        UserSettings stored = withExam(null);
        when(repository.findById(5L)).thenReturn(Optional.of(stored));
        trackExists(NOTARIALNA, "notarialna", "Notarialna", TrackKind.EXAM);

        LearningSettingsResponse response = service.get(authentication);

        assertThat(response.track().slug()).isEqualTo("notarialna");
        assertThat(response.examDate()).isNull();
        assertThat(response.plan()).isNull();
        verify(questionRepository, never()).findRefsInPool(any(), any());
    }

    @Test
    void get_planCountsOnlyQuestionsFromTheUsersPool_thatIsItsActs() {
        UserSettings stored = withExam(TODAY.plusDays(44));
        pool = Pools.of(SourceFilter.ALL, 1);
        loggedIn();
        when(repository.findById(5L)).thenReturn(Optional.of(stored));
        List<Question> all = new ArrayList<>();
        for (long id = 1; id <= 10; id++) all.add(QuestionRefs.question(id, 1, 1, QuestionRefs.OWN_SHEET));       // liczone
        for (long id = 11; id <= 20; id++) all.add(QuestionRefs.question(id, 1, 1, QuestionRefs.OTHER_SHEET));    // liczone: źródło wybiera się dopiero przy teście
        for (long id = 21; id <= 30; id++) all.add(QuestionRefs.question(id, 2, 1, QuestionRefs.OWN_SHEET));      // akt spoza puli
        givenQuestions(all);
        when(stateRepository.findScheduleByUserIdAndMode(5L, ReviewMode.TEST)).thenReturn(List.of());
        startedToday(0);

        LearningSettingsResponse.Plan plan = service.get(authentication).plan();

        assertThat(plan.newTestQuestionsLeft()).isEqualTo(20);
    }

    @Test
    void get_planDoesNotCountQuestionsStartedInFlashcardsOnly() {
        // „nowe" w planie to pytania nietknięte w TEŚCIE; fiszka tego samego pytania ma osobny stan
        UserSettings stored = withExam(TODAY.plusDays(44));
        loggedIn();
        when(repository.findById(5L)).thenReturn(Optional.of(stored));
        givenQuestionsInPool(20);
        when(stateRepository.findScheduleByUserIdAndMode(5L, ReviewMode.TEST)).thenReturn(List.of());
        startedToday(0);

        assertThat(service.get(authentication).plan().newTestQuestionsLeft()).isEqualTo(20);
        verify(stateRepository, never()).findScheduleByUserIdAndMode(5L, ReviewMode.FLASHCARD);
    }

    // ---------------------------------------------------------------- wygląd (ciemny / jasny)

    @Test
    void get_withoutStoredSettings_themeIsDark() {
        loggedIn();
        when(repository.findById(5L)).thenReturn(Optional.empty());

        assertThat(service.get(authentication).theme()).isEqualTo(Theme.DARK);
    }

    @Test
    void chooseTheme_createsSettingsWhenThereAreNone_andReturnsTheChoice() {
        loggedIn();
        when(repository.findById(5L)).thenReturn(Optional.empty());

        LearningSettingsResponse response = service.chooseTheme(new ChooseThemeRequest(Theme.LIGHT), authentication);

        ArgumentCaptor<UserSettings> captor = ArgumentCaptor.forClass(UserSettings.class);
        verify(repository).save(captor.capture());
        UserSettings saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(5L);
        assertThat(saved.getTheme()).isEqualTo(Theme.LIGHT);
        assertThat(saved.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 12, 12, 0));
        assertThat(response.theme()).isEqualTo(Theme.LIGHT);
    }

    @Test
    void chooseTheme_keepsEveryOtherSetting() {
        UserSettings existing = new UserSettings(5L);
        existing.setNewTestQuestionsPerDay(77);
        existing.setNewFlashcardsPerDay(9);
        existing.setTrackId(NOTARIALNA);
        loggedIn();
        when(repository.findById(5L)).thenReturn(Optional.of(existing));
        when(examTerms.findById(NOTARIALNA)).thenReturn(Optional.empty());

        LearningSettingsResponse response = service.chooseTheme(new ChooseThemeRequest(Theme.LIGHT), authentication);

        assertThat(existing.getTheme()).isEqualTo(Theme.LIGHT);
        assertThat(existing.getNewTestQuestionsPerDay()).isEqualTo(77);
        assertThat(existing.getNewFlashcardsPerDay()).isEqualTo(9);
        assertThat(existing.getTrackId()).isEqualTo(NOTARIALNA);
        assertThat(response.newTestQuestionsPerDay()).isEqualTo(77);
    }

    @Test
    void chooseTheme_canGoBackToDark() {
        UserSettings existing = new UserSettings(5L);
        existing.setTheme(Theme.LIGHT);
        loggedIn();
        when(repository.findById(5L)).thenReturn(Optional.of(existing));

        assertThat(service.chooseTheme(new ChooseThemeRequest(Theme.DARK), authentication).theme()).isEqualTo(Theme.DARK);
    }

    @Test
    void chooseTheme_rejectsAMissingTheme() {
        loggedIn();

        assertThatThrownBy(() -> service.chooseTheme(new ChooseThemeRequest(null), authentication))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void update_doesNotChangeTheTheme() {
        UserSettings existing = new UserSettings(5L);
        existing.setTheme(Theme.LIGHT);
        loggedIn();
        when(repository.findById(5L)).thenReturn(Optional.of(existing));

        LearningSettingsResponse response = service.update(request(null, null, null, null), authentication);

        assertThat(existing.getTheme()).isEqualTo(Theme.LIGHT);
        assertThat(response.theme()).isEqualTo(Theme.LIGHT);
    }

    // ---------------------------------------------------------------- limit nowych pytań: automatyczny albo własny

    /** {@code count} nowych pytań testowych (żadnego nie ruszono) i egzamin za {@code daysToExam} dni; zwraca zapisane ustawienia. */
    private UserSettings untouchedQuestionsAndExamIn(long daysToExam, int count) {
        UserSettings stored = withExam(TODAY.plusDays(daysToExam));
        when(repository.findById(5L)).thenReturn(Optional.of(stored));
        givenQuestionsInPool(count);
        when(stateRepository.findScheduleByUserIdAndMode(5L, ReviewMode.TEST)).thenReturn(List.of());
        startedToday(0);
        return stored;
    }

    @Test
    void get_autoLimit_followsThePlanWhenItNeedsMoreThanTheDefault() {
        untouchedQuestionsAndExamIn(44, 1500); // 30 dni planowania => 50 dziennie
        loggedIn();

        LearningSettingsResponse response = service.get(authentication);

        assertThat(response.newTestQuestionsAuto()).isTrue();
        assertThat(response.newTestQuestionsPerDay()).isEqualTo(50);
        assertThat(response.plan().requiredTestQuestionsPerDay()).isEqualTo(50);
        assertThat(response.plan().suggestedTestQuestionsPerDay()).isEqualTo(50);
        assertThat(response.plan().maxTestQuestionsPerDay()).isEqualTo(100);
    }

    @Test
    void get_autoLimit_neverDropsBelowTheDefault() {
        untouchedQuestionsAndExamIn(44, 90); // trzeba 3 dziennie, ale limit nie schodzi poniżej domyślnych 40
        loggedIn();

        LearningSettingsResponse response = service.get(authentication);

        assertThat(response.newTestQuestionsPerDay()).isEqualTo(40);
        assertThat(response.plan().requiredTestQuestionsPerDay()).isEqualTo(3);
    }

    @Test
    void get_autoLimit_hasACap_andThePlanAdmitsItCannotKeepUp() {
        untouchedQuestionsAndExamIn(44, 6000); // trzeba 200 dziennie, sufit 100
        loggedIn();

        LearningSettingsResponse response = service.get(authentication);

        assertThat(response.newTestQuestionsPerDay()).isEqualTo(100);
        assertThat(response.plan().requiredTestQuestionsPerDay()).isEqualTo(200);
        assertThat(response.plan().suggestedTestQuestionsPerDay()).isEqualTo(100);
        assertThat(response.plan().maxTestQuestionsPerDay()).isEqualTo(100);
    }

    @Test
    void get_autoLimit_withoutAnExamDateIsTheDefault() {
        loggedIn();
        when(repository.findById(5L)).thenReturn(Optional.empty());

        LearningSettingsResponse response = service.get(authentication);

        assertThat(response.newTestQuestionsAuto()).isTrue();
        assertThat(response.newTestQuestionsPerDay()).isEqualTo(40);
        assertThat(response.plan()).isNull();
    }

    @Test
    void get_ownLimit_isKeptEvenWhenThePlanNeedsMore() {
        UserSettings stored = untouchedQuestionsAndExamIn(44, 1500);
        stored.setNewTestQuestionsPerDay(25);
        loggedIn();

        LearningSettingsResponse response = service.get(authentication);

        assertThat(response.newTestQuestionsAuto()).isFalse();
        assertThat(response.newTestQuestionsPerDay()).isEqualTo(25);
        assertThat(response.plan().requiredTestQuestionsPerDay()).isEqualTo(50); // plan nadal mówi, ile trzeba
    }

    @Test
    void dailyLimit_ownLimitIsUsedAsIs_withoutLoadingAnyQuestions() {
        UserSettings stored = withExam(TODAY.plusDays(44));
        stored.setNewTestQuestionsPerDay(12);
        when(repository.findById(5L)).thenReturn(Optional.of(stored));

        assertThat(service.dailyNewTestQuestionLimit(5L)).isEqualTo(12);
        verify(questionRepository, never()).findRefsInPool(any(), any());
    }

    @Test
    void dailyLimit_withoutAnExam_isTheDefault_withoutLoadingAnyQuestions() {
        when(repository.findById(5L)).thenReturn(Optional.empty());

        assertThat(service.dailyNewTestQuestionLimit(5L)).isEqualTo(40);
        verify(questionRepository, never()).findRefsInPool(any(), any());
    }

    @Test
    void dailyLimit_inTheReviewReserve_isTheDefault_withoutLoadingAnyQuestions() {
        UserSettings stored = withExam(TODAY.plusDays(10));
        when(repository.findById(5L)).thenReturn(Optional.of(stored));

        assertThat(service.dailyNewTestQuestionLimit(5L)).isEqualTo(40);
        verify(questionRepository, never()).findRefsInPool(any(), any());
    }

    @Test
    void dailyLimit_auto_followsThePlan() {
        untouchedQuestionsAndExamIn(44, 1500);

        assertThat(service.dailyNewTestQuestionLimit(5L)).isEqualTo(50);
    }

    @Test
    void dailyLimit_auto_isStableDuringTheDay() {
        // Dziś zaczęto 20 z 6000 pytań: zostało 5980, ale limit nadal wynika ze stanu z początku dnia (6000 / 30 dni)
        UserSettings stored = withExam(TODAY.plusDays(44));
        when(repository.findById(5L)).thenReturn(Optional.of(stored));
        givenQuestionsInPool(6000);
        startedTests(1, 20);
        startedToday(20);

        // (5980 zostało + 20 zaczętych dziś) / 30 = 200 => sufit 100, tak samo jak rano
        assertThat(service.dailyNewTestQuestionLimit(5L)).isEqualTo(100);
    }

    @Test
    void dailyLimit_auto_countsOnlyQuestionsFromTheUsersPool() {
        UserSettings stored = withExam(TODAY.plusDays(44));
        pool = Pools.of(SourceFilter.ALL, 1);
        when(repository.findById(5L)).thenReturn(Optional.of(stored));
        List<Question> all = new ArrayList<>();
        for (long id = 1; id <= 1800; id++) all.add(QuestionRefs.question(id, 1, 1, QuestionRefs.OWN_SHEET));
        for (long id = 1801; id <= 3000; id++) all.add(QuestionRefs.question(id, 1, 1, QuestionRefs.OTHER_SHEET));
        givenQuestions(all);
        when(stateRepository.findScheduleByUserIdAndMode(5L, ReviewMode.TEST)).thenReturn(List.of());
        startedToday(0);

        assertThat(service.dailyNewTestQuestionLimit(5L)).isEqualTo(100); // (1800 + 1200) / 30
    }
}
