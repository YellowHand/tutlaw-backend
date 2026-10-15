package pl.tutlaw.backend.settings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import pl.tutlaw.backend.card.Card;
import pl.tutlaw.backend.card.CardRepository;
import pl.tutlaw.backend.card.CorrectOption;
import pl.tutlaw.backend.card.QuestionOrigin;
import pl.tutlaw.backend.review.ReviewLogRepository;
import pl.tutlaw.backend.review.ReviewMode;
import pl.tutlaw.backend.review.UserCardStateRepository;
import pl.tutlaw.backend.settings.dto.ChooseExamRequest;
import pl.tutlaw.backend.settings.dto.ChooseThemeRequest;
import pl.tutlaw.backend.settings.dto.LearningSettingsResponse;
import pl.tutlaw.backend.settings.dto.UpdateLearningSettingsRequest;
import pl.tutlaw.backend.testsupport.CardRefs;
import pl.tutlaw.backend.testsupport.MutableClock;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningSettingsServiceTest {

    // 2026-10-12 12:00 czasu warszawskiego
    private final MutableClock clock = MutableClock.at("2026-10-12T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);

    @Mock private UserSettingsRepository repository;
    @Mock private CardRepository cardRepository;
    @Mock private UserCardStateRepository stateRepository;
    @Mock private ExamTermRepository examTerms;
    @Mock private ReviewLogRepository reviewLogs;
    @Mock private CurrentUserResolver currentUserResolver;
    private final Authentication authentication = mock(Authentication.class);

    private LearningSettingsService service;

    @BeforeEach
    void setUp() {
        LearningDefaults defaults = new LearningDefaults(20, 40, 200, 0.9);
        service = new LearningSettingsService(repository, defaults, currentUserResolver, cardRepository, stateRepository, examTerms, reviewLogs, clock);
    }

    private User user() {
        User user = new User();
        user.setId(5L);
        return user;
    }

    private static UpdateLearningSettingsRequest request(Integer cards, Integer tests, Integer reviews, Double retention, Boolean shuffle) {
        return new UpdateLearningSettingsRequest(cards, tests, reviews, retention, shuffle);
    }

    /** Użytkownik z wybraną aplikacją (bez żadnych stubów — dla testów wołających {@code resolve} wprost). */
    private UserSettings chosen() {
        UserSettings stored = new UserSettings(5L);
        stored.setExamOrigin(QuestionOrigin.NOTARY);
        return stored;
    }

    /** Użytkownik z wybraną aplikacją, której termin to {@code examDate} (null = termin nieogłoszony). */
    private UserSettings withExam(LocalDate examDate) {
        UserSettings stored = chosen();
        ExamTerm term = new ExamTerm(QuestionOrigin.NOTARY);
        term.setExamDate(examDate);
        when(examTerms.findById(QuestionOrigin.NOTARY)).thenReturn(Optional.of(term));
        return stored;
    }

    // ---------------------------------------------------------------- wartości domyślne

    @Test
    void effective_withoutStoredSettings_usesDefaults() {
        when(repository.findById(5L)).thenReturn(Optional.empty());

        EffectiveSettings e = service.effectiveFor(5L);

        assertThat(e.newCardsPerDay()).isEqualTo(20);
        assertThat(e.newTestQuestionsPerDay()).isEqualTo(40);
        assertThat(e.maxReviewsPerDay()).isEqualTo(200);
        assertThat(e.desiredRetention()).isEqualTo(0.9);
        assertThat(e.effectiveRetention()).isEqualTo(0.9);
        assertThat(e.examDate()).isNull();
        assertThat(e.daysToExam()).isNull();
        assertThat(e.shuffleOptions()).isTrue();
        assertThat(e.retentionBoosted()).isFalse();
        assertThat(e.newTestQuestionsAuto()).isTrue(); // bez własnego limitu obowiązuje automatyczny
    }

    @Test
    void effective_storedValuesOverrideDefaults_nullFieldsFallBack() {
        UserSettings stored = new UserSettings(5L);
        stored.setNewCardsPerDay(5);
        stored.setMaxReviewsPerDay(80);
        stored.setShuffleOptions(false);
        when(repository.findById(5L)).thenReturn(Optional.of(stored));

        EffectiveSettings e = service.effectiveFor(5L);

        assertThat(e.newCardsPerDay()).isEqualTo(5);
        assertThat(e.newTestQuestionsPerDay()).isEqualTo(40);
        assertThat(e.maxReviewsPerDay()).isEqualTo(80);
        assertThat(e.shuffleOptions()).isFalse();
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
        UserSettings stored = chosen();

        EffectiveSettings e = service.resolve(stored, TODAY.plusDays(14), TODAY);

        assertThat(e.examOrigin()).isEqualTo(QuestionOrigin.NOTARY);
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

        assertThat(e.examOrigin()).isEqualTo(QuestionOrigin.NOTARY);
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
    void effective_withoutChosenApplication_doesNotLookUpTerms() {
        when(repository.findById(5L)).thenReturn(Optional.of(new UserSettings(5L)));

        EffectiveSettings e = service.effectiveFor(5L);

        assertThat(e.examOrigin()).isNull();
        assertThat(e.examDate()).isNull();
        verify(examTerms, never()).findById(any());
    }

    // ---------------------------------------------------------------- zapis

    @Test
    void update_storesAllFieldsAndReturnsThem() {
        when(currentUserResolver.resolve(authentication)).thenReturn(user());
        when(repository.findById(5L)).thenReturn(Optional.empty());

        LearningSettingsResponse response = service.update(request(10, 60, 150, 0.85, false), authentication);

        ArgumentCaptor<UserSettings> captor = ArgumentCaptor.forClass(UserSettings.class);
        verify(repository).save(captor.capture());
        UserSettings saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(5L);
        assertThat(saved.getNewCardsPerDay()).isEqualTo(10);
        assertThat(saved.getNewTestQuestionsPerDay()).isEqualTo(60);
        assertThat(saved.getMaxReviewsPerDay()).isEqualTo(150);
        assertThat(saved.getDesiredRetention()).isEqualTo(0.85);
        assertThat(saved.isShuffleOptions()).isFalse();
        assertThat(saved.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 12, 12, 0));

        assertThat(response.maxReviewsPerDay()).isEqualTo(150);
        assertThat(response.examOrigin()).isNull();
        assertThat(response.daysToExam()).isNull();
        assertThat(response.defaults().maxReviewsPerDay()).isEqualTo(200);
    }

    @Test
    void update_nullsMeanDefaults_andNullShuffleMeansYes() {
        UserSettings existing = new UserSettings(5L);
        existing.setNewCardsPerDay(3);
        when(currentUserResolver.resolve(authentication)).thenReturn(user());
        when(repository.findById(5L)).thenReturn(Optional.of(existing));

        LearningSettingsResponse response = service.update(request(null, null, null, null, null), authentication);

        assertThat(existing.getNewCardsPerDay()).isNull();
        assertThat(existing.isShuffleOptions()).isTrue();
        assertThat(response.newCardsPerDay()).isEqualTo(20);
        assertThat(response.examDate()).isNull();
        assertThat(response.plan()).isNull();
    }

    @Test
    void update_doesNotChangeTheChosenExamNorTheOriginFilter() {
        UserSettings existing = withExam(TODAY.plusDays(90));
        existing.setTestOrigins("NOTARY,AUTHORED");
        when(currentUserResolver.resolve(authentication)).thenReturn(user());
        when(repository.findById(5L)).thenReturn(Optional.of(existing));
        when(cardRepository.findReadableRefsForUser(5L)).thenReturn(List.of());
        when(stateRepository.findCardDueByUserId(5L)).thenReturn(List.of());

        LearningSettingsResponse response = service.update(request(null, 50, null, null, true), authentication);

        assertThat(existing.getExamOrigin()).isEqualTo(QuestionOrigin.NOTARY);
        assertThat(existing.getTestOrigins()).isEqualTo("NOTARY,AUTHORED");
        assertThat(response.examOrigin()).isEqualTo(QuestionOrigin.NOTARY);
        assertThat(response.daysToExam()).isEqualTo(90L);
    }

    @Test
    void update_rejectsRetentionOutsideTheAllowedRange() {
        when(currentUserResolver.resolve(authentication)).thenReturn(user());

        assertThatThrownBy(() -> service.update(request(null, null, null, 0.5, null), authentication))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("retencja");
        assertThatThrownBy(() -> service.update(request(null, null, null, 0.99, null), authentication))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.update(request(null, null, null, Double.NaN, null), authentication))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    // ---------------------------------------------------------------- wybór egzaminu

    @Test
    void chooseExam_storesTheApplication_andSetsTheOriginFilterToItPlusAuthored() {
        when(currentUserResolver.resolve(authentication)).thenReturn(user());
        when(repository.findById(5L)).thenReturn(Optional.empty());
        ExamTerm term = new ExamTerm(QuestionOrigin.BAILIFF);
        term.setExamDate(TODAY.plusDays(100));
        when(examTerms.findById(QuestionOrigin.BAILIFF)).thenReturn(Optional.of(term));
        when(cardRepository.findReadableRefsForUser(5L)).thenReturn(List.of());
        when(stateRepository.findCardDueByUserId(5L)).thenReturn(List.of());

        LearningSettingsResponse response = service.chooseExam(new ChooseExamRequest(QuestionOrigin.BAILIFF, null), authentication);

        ArgumentCaptor<UserSettings> captor = ArgumentCaptor.forClass(UserSettings.class);
        verify(repository).save(captor.capture());
        UserSettings saved = captor.getValue();
        assertThat(saved.getExamOrigin()).isEqualTo(QuestionOrigin.BAILIFF);
        assertThat(OriginFilterService.parse(saved.getTestOrigins()))
                .isEqualTo(EnumSet.of(QuestionOrigin.BAILIFF, QuestionOrigin.AUTHORED));
        assertThat(saved.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 12, 12, 0));

        assertThat(response.examOrigin()).isEqualTo(QuestionOrigin.BAILIFF);
        assertThat(response.examDate()).isEqualTo(TODAY.plusDays(100));
        assertThat(response.daysToExam()).isEqualTo(100L);
    }

    @Test
    void chooseExam_keepsOtherSettings_andReplacesAnEarlierChoice() {
        UserSettings existing = new UserSettings(5L);
        existing.setNewTestQuestionsPerDay(77);
        existing.setExamOrigin(QuestionOrigin.NOTARY);
        existing.setTestOrigins("NOTARY,AUTHORED");
        when(currentUserResolver.resolve(authentication)).thenReturn(user());
        when(repository.findById(5L)).thenReturn(Optional.of(existing));
        when(examTerms.findById(QuestionOrigin.JUDGE_PROSECUTOR)).thenReturn(Optional.empty());

        LearningSettingsResponse response = service.chooseExam(new ChooseExamRequest(QuestionOrigin.JUDGE_PROSECUTOR, null), authentication);

        assertThat(existing.getNewTestQuestionsPerDay()).isEqualTo(77);
        assertThat(existing.getExamOrigin()).isEqualTo(QuestionOrigin.JUDGE_PROSECUTOR);
        assertThat(existing.getTestOrigins()).isEqualTo("AUTHORED,JUDGE_PROSECUTOR");
        assertThat(response.examDate()).isNull(); // termin nieogłoszony
        assertThat(response.plan()).isNull();
    }

    @Test
    void chooseExam_rejectsAuthoredQuestions() {
        when(currentUserResolver.resolve(authentication)).thenReturn(user());

        assertThatThrownBy(() -> service.chooseExam(new ChooseExamRequest(QuestionOrigin.AUTHORED, null), authentication))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("autorskie");
        assertThatThrownBy(() -> service.chooseExam(new ChooseExamRequest(null, null), authentication))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void chooseExam_marksTheStartChoiceAsMade() {
        when(currentUserResolver.resolve(authentication)).thenReturn(user());
        when(repository.findById(5L)).thenReturn(Optional.empty());
        when(examTerms.findById(QuestionOrigin.NOTARY)).thenReturn(Optional.empty());

        LearningSettingsResponse response = service.chooseExam(new ChooseExamRequest(QuestionOrigin.NOTARY, null), authentication);

        assertThat(response.startChoiceMade()).isTrue();
    }

    @Test
    void chooseExam_asAStudent_hasNoApplicationNoExamDateAndNoOriginFilter() {
        UserSettings existing = new UserSettings(5L);
        existing.setExamOrigin(QuestionOrigin.NOTARY);
        existing.setTestOrigins("AUTHORED,NOTARY");
        existing.setStartChoiceMade(true);
        when(currentUserResolver.resolve(authentication)).thenReturn(user());
        when(repository.findById(5L)).thenReturn(Optional.of(existing));

        LearningSettingsResponse response = service.chooseExam(new ChooseExamRequest(null, true), authentication);

        assertThat(existing.getExamOrigin()).isNull();
        assertThat(existing.getTestOrigins()).isNull();
        assertThat(response.examOrigin()).isNull();
        assertThat(response.examDate()).isNull();
        assertThat(response.daysToExam()).isNull();
        assertThat(response.plan()).isNull();
        assertThat(response.startChoiceMade()).isTrue();
    }

    @Test
    void chooseExam_asAStudent_ignoresAnApplicationSentAlong() {
        when(currentUserResolver.resolve(authentication)).thenReturn(user());
        when(repository.findById(5L)).thenReturn(Optional.empty());

        service.chooseExam(new ChooseExamRequest(QuestionOrigin.BAILIFF, true), authentication);

        ArgumentCaptor<UserSettings> captor = ArgumentCaptor.forClass(UserSettings.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getExamOrigin()).isNull();
        assertThat(captor.getValue().isStartChoiceMade()).isTrue();
    }

    @Test
    void chooseExam_studentCanChooseAnApplicationLater() {
        UserSettings student = new UserSettings(5L);
        student.setStartChoiceMade(true);
        when(currentUserResolver.resolve(authentication)).thenReturn(user());
        when(repository.findById(5L)).thenReturn(Optional.of(student));
        when(examTerms.findById(QuestionOrigin.JUDGE_PROSECUTOR)).thenReturn(Optional.empty());

        LearningSettingsResponse response = service.chooseExam(new ChooseExamRequest(QuestionOrigin.JUDGE_PROSECUTOR, null), authentication);

        assertThat(response.examOrigin()).isEqualTo(QuestionOrigin.JUDGE_PROSECUTOR);
        assertThat(OriginFilterService.parse(student.getTestOrigins()))
                .isEqualTo(EnumSet.of(QuestionOrigin.JUDGE_PROSECUTOR, QuestionOrigin.AUTHORED));
    }

    @Test
    void get_reportsTheStartChoiceOnlyOnceMade() {
        when(currentUserResolver.resolve(authentication)).thenReturn(user());
        when(repository.findById(5L)).thenReturn(Optional.empty());
        assertThat(service.get(authentication).startChoiceMade()).isFalse();

        UserSettings stored = new UserSettings(5L);
        when(repository.findById(5L)).thenReturn(Optional.of(stored));
        assertThat(service.get(authentication).startChoiceMade()).isFalse(); // wiersz jest, ale wyboru nie było

        stored.setStartChoiceMade(true);
        assertThat(service.get(authentication).startChoiceMade()).isTrue();
    }

    // ---------------------------------------------------------------- plan

    private static Card card(long id, boolean test) {
        Card card = new Card();
        card.setId(id);
        if (test) card.setCorrectOption(CorrectOption.A);
        return card;
    }

    private static UserCardStateRepository.CardDue started(long cardId) {
        return new UserCardStateRepository.CardDue() {
            public Long getCardId() { return cardId; }
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

    @Test
    void get_withExamDate_buildsPlanFromUntouchedCardsOnly() {
        UserSettings stored = withExam(TODAY.plusDays(44)); // 44 - 14 rezerwy = 30 dni planowania
        when(currentUserResolver.resolve(authentication)).thenReturn(user());
        when(repository.findById(5L)).thenReturn(Optional.of(stored));
        List<Card> readable = new java.util.ArrayList<>();
        for (long id = 1; id <= 100; id++) readable.add(card(id, id <= 70)); // 70 pytań testowych, 30 fiszek
        when(cardRepository.findReadableRefsForUser(5L)).thenReturn(CardRefs.of(readable));
        // użytkownik ruszył już 10 pytań testowych (1..10) i 5 fiszek (71..75)
        List<UserCardStateRepository.CardDue> schedule = new java.util.ArrayList<>();
        for (long id = 1; id <= 10; id++) schedule.add(started(id));
        for (long id = 71; id <= 75; id++) schedule.add(started(id));
        when(stateRepository.findCardDueByUserId(5L)).thenReturn(schedule);
        startedToday(0);

        LearningSettingsResponse.Plan plan = service.get(authentication).plan();

        assertThat(plan.newTestQuestionsLeft()).isEqualTo(60);
        assertThat(plan.newFlashcardsLeft()).isEqualTo(25);
        assertThat(plan.planningDays()).isEqualTo(30);
        assertThat(plan.suggestedTestQuestionsPerDay()).isEqualTo(2);
        assertThat(plan.suggestedFlashcardsPerDay()).isEqualTo(1);
        assertThat(plan.newTestQuestionsToday()).isEqualTo(0);
    }

    @Test
    void get_todaysTargetIsComputedFromTheStartOfTheDay_soItDoesNotShrinkWhileLearning() {
        UserSettings stored = withExam(TODAY.plusDays(44)); // 30 dni planowania
        when(currentUserResolver.resolve(authentication)).thenReturn(user());
        when(repository.findById(5L)).thenReturn(Optional.of(stored));
        List<Card> readable = new java.util.ArrayList<>();
        for (long id = 1; id <= 300; id++) readable.add(card(id, true));
        when(cardRepository.findReadableRefsForUser(5L)).thenReturn(CardRefs.of(readable));
        // 300 pytań, dziś zaczęto 20 (1..20): zostało 280, a cel dnia nadal wynika z 300 / 30 = 10
        List<UserCardStateRepository.CardDue> schedule = new java.util.ArrayList<>();
        for (long id = 1; id <= 20; id++) schedule.add(started(id));
        when(stateRepository.findCardDueByUserId(5L)).thenReturn(schedule);
        startedToday(20);

        LearningSettingsResponse.Plan plan = service.get(authentication).plan();

        assertThat(plan.newTestQuestionsLeft()).isEqualTo(280);
        assertThat(plan.suggestedTestQuestionsPerDay()).isEqualTo(10);
        assertThat(plan.newTestQuestionsToday()).isEqualTo(20);
    }

    @Test
    void get_inTheLastTwoWeeks_planIsReviewOnly() {
        UserSettings stored = withExam(TODAY.plusDays(14));
        when(currentUserResolver.resolve(authentication)).thenReturn(user());
        when(repository.findById(5L)).thenReturn(Optional.of(stored));
        List<Card> readable = new java.util.ArrayList<>();
        for (long id = 1; id <= 50; id++) readable.add(card(id, true));
        when(cardRepository.findReadableRefsForUser(5L)).thenReturn(CardRefs.of(readable));
        when(stateRepository.findCardDueByUserId(5L)).thenReturn(List.of());
        startedToday(0);

        LearningSettingsResponse.Plan plan = service.get(authentication).plan();

        assertThat(plan.newTestQuestionsLeft()).isEqualTo(50);
        assertThat(plan.planningDays()).isEqualTo(0);
        assertThat(plan.suggestedTestQuestionsPerDay()).isEqualTo(0); // nie "50 dziennie": zostają same powtórki
        assertThat(plan.suggestedFlashcardsPerDay()).isEqualTo(0);
    }

    @Test
    void get_withoutChosenApplication_hasNoPlan_andDoesNotLoadCards() {
        when(currentUserResolver.resolve(authentication)).thenReturn(user());
        when(repository.findById(5L)).thenReturn(Optional.empty());

        LearningSettingsResponse response = service.get(authentication);

        assertThat(response.plan()).isNull();
        assertThat(response.examOrigin()).isNull();
        verify(cardRepository, never()).findReadableRefsForUser(any());
    }

    @Test
    void get_applicationWithoutAnnouncedTerm_hasNoPlan() {
        when(currentUserResolver.resolve(authentication)).thenReturn(user());
        UserSettings stored = withExam(null);
        when(repository.findById(5L)).thenReturn(Optional.of(stored));

        LearningSettingsResponse response = service.get(authentication);

        assertThat(response.examOrigin()).isEqualTo(QuestionOrigin.NOTARY);
        assertThat(response.examDate()).isNull();
        assertThat(response.plan()).isNull();
        verify(cardRepository, never()).findReadableRefsForUser(any());
    }

    @Test
    void get_planCountsOnlyQuestionsFromTheSelectedOrigins_andAllFlashcards() {
        UserSettings stored = withExam(TODAY.plusDays(44));
        stored.setTestOrigins("NOTARY,BAILIFF");
        when(currentUserResolver.resolve(authentication)).thenReturn(user());
        when(repository.findById(5L)).thenReturn(Optional.of(stored));
        List<Card> readable = new java.util.ArrayList<>();
        for (long id = 1; id <= 40; id++) {
            Card card = card(id, id <= 30); // 30 pytań testowych, 10 fiszek
            card.setOrigin(id <= 10 ? QuestionOrigin.NOTARY : id <= 20 ? QuestionOrigin.BAILIFF : QuestionOrigin.AUTHORED);
            readable.add(card);
        }
        when(cardRepository.findReadableRefsForUser(5L)).thenReturn(CardRefs.of(readable));
        when(stateRepository.findCardDueByUserId(5L)).thenReturn(List.of());
        startedToday(0);

        LearningSettingsResponse.Plan plan = service.get(authentication).plan();

        assertThat(plan.newTestQuestionsLeft()).isEqualTo(20); // tylko NOTARY i BAILIFF
        assertThat(plan.newFlashcardsLeft()).isEqualTo(10);    // fiszki bez filtra
    }

    // ---------------------------------------------------------------- wygląd (ciemny / jasny)

    @Test
    void get_withoutStoredSettings_themeIsDark() {
        when(currentUserResolver.resolve(authentication)).thenReturn(user());
        when(repository.findById(5L)).thenReturn(Optional.empty());

        assertThat(service.get(authentication).theme()).isEqualTo(Theme.DARK);
    }

    @Test
    void chooseTheme_createsSettingsWhenThereAreNone_andReturnsTheChoice() {
        when(currentUserResolver.resolve(authentication)).thenReturn(user());
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
        existing.setNewCardsPerDay(9);
        existing.setExamOrigin(QuestionOrigin.NOTARY);
        existing.setTestOrigins("NOTARY,AUTHORED");
        existing.setShuffleOptions(false);
        when(currentUserResolver.resolve(authentication)).thenReturn(user());
        when(repository.findById(5L)).thenReturn(Optional.of(existing));
        when(examTerms.findById(QuestionOrigin.NOTARY)).thenReturn(Optional.empty());

        LearningSettingsResponse response = service.chooseTheme(new ChooseThemeRequest(Theme.LIGHT), authentication);

        assertThat(existing.getTheme()).isEqualTo(Theme.LIGHT);
        assertThat(existing.getNewTestQuestionsPerDay()).isEqualTo(77);
        assertThat(existing.getNewCardsPerDay()).isEqualTo(9);
        assertThat(existing.getExamOrigin()).isEqualTo(QuestionOrigin.NOTARY);
        assertThat(existing.getTestOrigins()).isEqualTo("NOTARY,AUTHORED");
        assertThat(existing.isShuffleOptions()).isFalse();
        assertThat(response.newTestQuestionsPerDay()).isEqualTo(77);
    }

    @Test
    void chooseTheme_canGoBackToDark() {
        UserSettings existing = new UserSettings(5L);
        existing.setTheme(Theme.LIGHT);
        when(currentUserResolver.resolve(authentication)).thenReturn(user());
        when(repository.findById(5L)).thenReturn(Optional.of(existing));

        assertThat(service.chooseTheme(new ChooseThemeRequest(Theme.DARK), authentication).theme()).isEqualTo(Theme.DARK);
    }

    @Test
    void chooseTheme_rejectsAMissingTheme() {
        when(currentUserResolver.resolve(authentication)).thenReturn(user());

        assertThatThrownBy(() -> service.chooseTheme(new ChooseThemeRequest(null), authentication))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void update_doesNotChangeTheTheme() {
        UserSettings existing = new UserSettings(5L);
        existing.setTheme(Theme.LIGHT);
        when(currentUserResolver.resolve(authentication)).thenReturn(user());
        when(repository.findById(5L)).thenReturn(Optional.of(existing));

        LearningSettingsResponse response = service.update(request(null, null, null, null, null), authentication);

        assertThat(existing.getTheme()).isEqualTo(Theme.LIGHT);
        assertThat(response.theme()).isEqualTo(Theme.LIGHT);
    }

    // ---------------------------------------------------------------- limit nowych pytań: automatyczny albo własny

    /** {@code count} nowych pytań testowych (żadnego nie ruszono) i egzamin za {@code daysToExam} dni; zwraca zapisane ustawienia. */
    private UserSettings untouchedQuestionsAndExamIn(long daysToExam, int count) {
        UserSettings stored = withExam(TODAY.plusDays(daysToExam));
        when(repository.findById(5L)).thenReturn(Optional.of(stored));
        List<Card> readable = new java.util.ArrayList<>();
        for (long id = 1; id <= count; id++) readable.add(card(id, true));
        when(cardRepository.findReadableRefsForUser(5L)).thenReturn(CardRefs.of(readable));
        when(stateRepository.findCardDueByUserId(5L)).thenReturn(List.of());
        startedToday(0);
        return stored;
    }

    @Test
    void get_autoLimit_followsThePlanWhenItNeedsMoreThanTheDefault() {
        untouchedQuestionsAndExamIn(44, 1500); // 30 dni planowania => 50 dziennie
        when(currentUserResolver.resolve(authentication)).thenReturn(user());

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
        when(currentUserResolver.resolve(authentication)).thenReturn(user());

        LearningSettingsResponse response = service.get(authentication);

        assertThat(response.newTestQuestionsPerDay()).isEqualTo(40);
        assertThat(response.plan().requiredTestQuestionsPerDay()).isEqualTo(3);
    }

    @Test
    void get_autoLimit_hasACap_andThePlanAdmitsItCannotKeepUp() {
        untouchedQuestionsAndExamIn(44, 6000); // trzeba 200 dziennie, sufit 100
        when(currentUserResolver.resolve(authentication)).thenReturn(user());

        LearningSettingsResponse response = service.get(authentication);

        assertThat(response.newTestQuestionsPerDay()).isEqualTo(100);
        assertThat(response.plan().requiredTestQuestionsPerDay()).isEqualTo(200);
        assertThat(response.plan().suggestedTestQuestionsPerDay()).isEqualTo(100);
        assertThat(response.plan().maxTestQuestionsPerDay()).isEqualTo(100);
    }

    @Test
    void get_autoLimit_withoutAnExamDateIsTheDefault() {
        when(currentUserResolver.resolve(authentication)).thenReturn(user());
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
        when(currentUserResolver.resolve(authentication)).thenReturn(user());

        LearningSettingsResponse response = service.get(authentication);

        assertThat(response.newTestQuestionsAuto()).isFalse();
        assertThat(response.newTestQuestionsPerDay()).isEqualTo(25);
        assertThat(response.plan().requiredTestQuestionsPerDay()).isEqualTo(50); // plan nadal mówi, ile trzeba
    }

    @Test
    void dailyLimit_ownLimitIsUsedAsIs_withoutLoadingAnyCards() {
        UserSettings stored = withExam(TODAY.plusDays(44));
        stored.setNewTestQuestionsPerDay(12);
        when(repository.findById(5L)).thenReturn(Optional.of(stored));

        assertThat(service.dailyNewTestQuestionLimit(5L)).isEqualTo(12);
        verify(cardRepository, never()).findReadableRefsForUser(any());
    }

    @Test
    void dailyLimit_withoutAnExam_isTheDefault_withoutLoadingAnyCards() {
        when(repository.findById(5L)).thenReturn(Optional.empty());

        assertThat(service.dailyNewTestQuestionLimit(5L)).isEqualTo(40);
        verify(cardRepository, never()).findReadableRefsForUser(any());
    }

    @Test
    void dailyLimit_inTheReviewReserve_isTheDefault_withoutLoadingAnyCards() {
        UserSettings stored = withExam(TODAY.plusDays(10));
        when(repository.findById(5L)).thenReturn(Optional.of(stored));

        assertThat(service.dailyNewTestQuestionLimit(5L)).isEqualTo(40);
        verify(cardRepository, never()).findReadableRefsForUser(any());
    }

    @Test
    void dailyLimit_auto_followsThePlan() {
        untouchedQuestionsAndExamIn(44, 1500);

        assertThat(service.dailyNewTestQuestionLimit(5L)).isEqualTo(50);
    }

    @Test
    void dailyLimit_auto_isStableDuringTheDay() {
        // Dziś zaczęto 20 z 300 pytań: zostało 280, ale limit nadal wynika ze stanu z początku dnia (300 / 30 dni)
        UserSettings stored = withExam(TODAY.plusDays(44));
        when(repository.findById(5L)).thenReturn(Optional.of(stored));
        List<Card> readable = new java.util.ArrayList<>();
        for (long id = 1; id <= 6000; id++) readable.add(card(id, true));
        when(cardRepository.findReadableRefsForUser(5L)).thenReturn(CardRefs.of(readable));
        List<UserCardStateRepository.CardDue> schedule = new java.util.ArrayList<>();
        for (long id = 1; id <= 20; id++) schedule.add(started(id));
        when(stateRepository.findCardDueByUserId(5L)).thenReturn(schedule);
        startedToday(20);

        // (5980 zostało + 20 zaczętych dziś) / 30 = 200 => sufit 100, tak samo jak rano
        assertThat(service.dailyNewTestQuestionLimit(5L)).isEqualTo(100);
    }

    @Test
    void dailyLimit_auto_countsOnlyQuestionsFromTheSelectedOrigins() {
        UserSettings stored = withExam(TODAY.plusDays(44));
        stored.setTestOrigins("NOTARY");
        when(repository.findById(5L)).thenReturn(Optional.of(stored));
        List<Card> readable = new java.util.ArrayList<>();
        for (long id = 1; id <= 3000; id++) {
            Card card = card(id, true);
            card.setOrigin(id <= 1800 ? QuestionOrigin.NOTARY : QuestionOrigin.BAILIFF);
            readable.add(card);
        }
        when(cardRepository.findReadableRefsForUser(5L)).thenReturn(CardRefs.of(readable));
        when(stateRepository.findCardDueByUserId(5L)).thenReturn(List.of());
        startedToday(0);

        assertThat(service.dailyNewTestQuestionLimit(5L)).isEqualTo(60); // 1800 / 30
    }
}
