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
import pl.tutlaw.backend.review.UserCardStateRepository;
import pl.tutlaw.backend.settings.dto.LearningSettingsResponse;
import pl.tutlaw.backend.settings.dto.UpdateLearningSettingsRequest;
import pl.tutlaw.backend.testsupport.MutableClock;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    @Mock private CurrentUserResolver currentUserResolver;
    private final Authentication authentication = mock(Authentication.class);

    private LearningSettingsService service;

    @BeforeEach
    void setUp() {
        LearningDefaults defaults = new LearningDefaults(20, 40, 200, 0.9);
        service = new LearningSettingsService(repository, defaults, currentUserResolver, cardRepository, stateRepository, clock);
    }

    private User user() {
        User user = new User();
        user.setId(5L);
        return user;
    }

    private static UpdateLearningSettingsRequest request(Integer cards, Integer tests, Integer reviews, Double retention, LocalDate exam, Boolean shuffle) {
        return new UpdateLearningSettingsRequest(cards, tests, reviews, retention, exam, shuffle);
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
    }

    // ---------------------------------------------------------------- tryb egzaminacyjny

    @Test
    void effective_examInTwoWeeks_raisesRetention() {
        UserSettings stored = new UserSettings(5L);
        stored.setExamDate(TODAY.plusDays(14));

        EffectiveSettings e = service.resolve(stored, TODAY);

        assertThat(e.daysToExam()).isEqualTo(14L);
        assertThat(e.desiredRetention()).isEqualTo(0.9);
        assertThat(e.effectiveRetention()).isEqualTo(0.95);
        assertThat(e.retentionBoosted()).isTrue();
        assertThat(e.examPassed()).isFalse();
    }

    @Test
    void effective_examFarAway_doesNotRaiseRetention() {
        UserSettings stored = new UserSettings(5L);
        stored.setExamDate(TODAY.plusDays(120));

        EffectiveSettings e = service.resolve(stored, TODAY);

        assertThat(e.daysToExam()).isEqualTo(120L);
        assertThat(e.retentionBoosted()).isFalse();
    }

    @Test
    void effective_examOnTheDay_countsAsZeroDays() {
        UserSettings stored = new UserSettings(5L);
        stored.setExamDate(TODAY);

        assertThat(service.resolve(stored, TODAY).daysToExam()).isEqualTo(0L);
    }

    @Test
    void effective_examAlreadyPassed_switchesTheExamModeOff() {
        UserSettings stored = new UserSettings(5L);
        stored.setExamDate(TODAY.minusDays(1));

        EffectiveSettings e = service.resolve(stored, TODAY);

        assertThat(e.daysToExam()).isNull();
        assertThat(e.examPassed()).isTrue();
        assertThat(e.effectiveRetention()).isEqualTo(0.9);
    }

    // ---------------------------------------------------------------- zapis

    @Test
    void update_storesAllFieldsAndReturnsThem() {
        when(currentUserResolver.resolve(authentication)).thenReturn(user());
        when(repository.findById(5L)).thenReturn(Optional.empty());

        LearningSettingsResponse response = service.update(
                request(10, 60, 150, 0.85, TODAY.plusDays(90), false), authentication);

        ArgumentCaptor<UserSettings> captor = ArgumentCaptor.forClass(UserSettings.class);
        verify(repository).save(captor.capture());
        UserSettings saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(5L);
        assertThat(saved.getNewCardsPerDay()).isEqualTo(10);
        assertThat(saved.getNewTestQuestionsPerDay()).isEqualTo(60);
        assertThat(saved.getMaxReviewsPerDay()).isEqualTo(150);
        assertThat(saved.getDesiredRetention()).isEqualTo(0.85);
        assertThat(saved.getExamDate()).isEqualTo(TODAY.plusDays(90));
        assertThat(saved.isShuffleOptions()).isFalse();
        assertThat(saved.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 12, 12, 0));

        assertThat(response.maxReviewsPerDay()).isEqualTo(150);
        assertThat(response.daysToExam()).isEqualTo(90L);
        assertThat(response.defaults().maxReviewsPerDay()).isEqualTo(200);
    }

    @Test
    void update_nullsMeanDefaults_andNullShuffleMeansYes() {
        UserSettings existing = new UserSettings(5L);
        existing.setNewCardsPerDay(3);
        existing.setExamDate(TODAY.plusDays(10));
        when(currentUserResolver.resolve(authentication)).thenReturn(user());
        when(repository.findById(5L)).thenReturn(Optional.of(existing));

        LearningSettingsResponse response = service.update(request(null, null, null, null, null, null), authentication);

        assertThat(existing.getNewCardsPerDay()).isNull();
        assertThat(existing.getExamDate()).isNull();
        assertThat(existing.isShuffleOptions()).isTrue();
        assertThat(response.newCardsPerDay()).isEqualTo(20);
        assertThat(response.examDate()).isNull();
        assertThat(response.plan()).isNull();
    }

    @Test
    void update_rejectsRetentionOutsideTheAllowedRange() {
        when(currentUserResolver.resolve(authentication)).thenReturn(user());

        assertThatThrownBy(() -> service.update(request(null, null, null, 0.5, null, null), authentication))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("retencja");
        assertThatThrownBy(() -> service.update(request(null, null, null, 0.99, null, null), authentication))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.update(request(null, null, null, Double.NaN, null, null), authentication))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void update_rejectsExamInThePastOrTooFarAway() {
        when(currentUserResolver.resolve(authentication)).thenReturn(user());

        assertThatThrownBy(() -> service.update(request(null, null, null, null, TODAY.minusDays(1), null), authentication))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("przeszłości");
        assertThatThrownBy(() -> service.update(request(null, null, null, null, TODAY.plusYears(4), null), authentication))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("odległa");
        verify(repository, never()).save(any());
    }

    @Test
    void update_examToday_isAllowed() {
        when(currentUserResolver.resolve(authentication)).thenReturn(user());
        when(repository.findById(5L)).thenReturn(Optional.empty());
        when(cardRepository.findReadableCardsForUser(5L)).thenReturn(List.of());
        when(stateRepository.findCardDueByUserId(5L)).thenReturn(List.of());

        LearningSettingsResponse response = service.update(request(null, null, null, null, TODAY, null), authentication);

        assertThat(response.daysToExam()).isEqualTo(0L);
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

    @Test
    void get_withExamDate_buildsPlanFromUntouchedCardsOnly() {
        UserSettings stored = new UserSettings(5L);
        stored.setExamDate(TODAY.plusDays(37)); // 37 - 7 rezerwy = 30 dni planowania
        when(currentUserResolver.resolve(authentication)).thenReturn(user());
        when(repository.findById(5L)).thenReturn(Optional.of(stored));
        List<Card> readable = new java.util.ArrayList<>();
        for (long id = 1; id <= 100; id++) readable.add(card(id, id <= 70)); // 70 pytań testowych, 30 fiszek
        when(cardRepository.findReadableCardsForUser(5L)).thenReturn(readable);
        // użytkownik ruszył już 10 pytań testowych (1..10) i 5 fiszek (71..75)
        List<UserCardStateRepository.CardDue> schedule = new java.util.ArrayList<>();
        for (long id = 1; id <= 10; id++) schedule.add(started(id));
        for (long id = 71; id <= 75; id++) schedule.add(started(id));
        when(stateRepository.findCardDueByUserId(5L)).thenReturn(schedule);

        LearningSettingsResponse.Plan plan = service.get(authentication).plan();

        assertThat(plan.newTestQuestionsLeft()).isEqualTo(60);
        assertThat(plan.newFlashcardsLeft()).isEqualTo(25);
        assertThat(plan.planningDays()).isEqualTo(30);
        assertThat(plan.suggestedTestQuestionsPerDay()).isEqualTo(2);
        assertThat(plan.suggestedFlashcardsPerDay()).isEqualTo(1);
    }

    @Test
    void get_withoutExamDate_hasNoPlan_andDoesNotLoadCards() {
        when(currentUserResolver.resolve(authentication)).thenReturn(user());
        when(repository.findById(5L)).thenReturn(Optional.empty());

        LearningSettingsResponse response = service.get(authentication);

        assertThat(response.plan()).isNull();
        verify(cardRepository, never()).findReadableCardsForUser(any());
    }
}
