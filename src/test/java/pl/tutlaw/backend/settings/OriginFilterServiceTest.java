package pl.tutlaw.backend.settings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import pl.tutlaw.backend.card.CardRepository;
import pl.tutlaw.backend.card.QuestionOrigin;
import pl.tutlaw.backend.settings.dto.OriginFilterRequest;
import pl.tutlaw.backend.settings.dto.OriginFilterResponse;
import pl.tutlaw.backend.testsupport.MutableClock;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.util.EnumSet;
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
class OriginFilterServiceTest {

    private final MutableClock clock = MutableClock.at("2026-10-12T10:00:00Z");

    @Mock private UserSettingsRepository repository;
    @Mock private CardRepository cardRepository;
    @Mock private CurrentUserResolver currentUserResolver;
    private final Authentication authentication = mock(Authentication.class);

    private OriginFilterService service;

    @BeforeEach
    void setUp() {
        service = new OriginFilterService(repository, cardRepository, currentUserResolver, clock);
    }

    private void signedIn() {
        User user = new User();
        user.setId(5L);
        when(currentUserResolver.resolve(authentication)).thenReturn(user);
    }

    private static CardRepository.OriginCount count(QuestionOrigin origin, long total) {
        return new CardRepository.OriginCount() {
            public QuestionOrigin getOrigin() { return origin; }
            public Long getTotal() { return total; }
        };
    }

    // ---------------------------------------------------------------- odczyt i zapis w kolumnie

    @Test
    void parse_nullBlankAndGarbageMeanEverything() {
        assertThat(OriginFilterService.parse(null)).isEqualTo(EnumSet.allOf(QuestionOrigin.class));
        assertThat(OriginFilterService.parse("  ")).isEqualTo(EnumSet.allOf(QuestionOrigin.class));
        assertThat(OriginFilterService.parse("NIEZNANE")).isEqualTo(EnumSet.allOf(QuestionOrigin.class));
    }

    @Test
    void parse_readsKnownCodesAndSkipsUnknownOnes() {
        assertThat(OriginFilterService.parse("NOTARY, BAILIFF,STARY_KOD"))
                .isEqualTo(EnumSet.of(QuestionOrigin.NOTARY, QuestionOrigin.BAILIFF));
    }

    @Test
    void serialize_everythingIsNull_andPartialListsKeepEnumOrder() {
        assertThat(OriginFilterService.serialize(EnumSet.allOf(QuestionOrigin.class))).isNull();
        assertThat(OriginFilterService.serialize(EnumSet.of(QuestionOrigin.BAILIFF, QuestionOrigin.AUTHORED)))
                .isEqualTo("AUTHORED,BAILIFF");
    }

    @Test
    void serializeThenParse_roundTrips() {
        for (QuestionOrigin only : QuestionOrigin.values()) {
            EnumSet<QuestionOrigin> one = EnumSet.of(only);
            assertThat(OriginFilterService.parse(OriginFilterService.serialize(one))).isEqualTo(one);
        }
    }

    // ---------------------------------------------------------------- aktywne pochodzenia

    @Test
    void activeFor_withoutStoredSettingsIsEverything() {
        when(repository.findById(5L)).thenReturn(Optional.empty());

        assertThat(service.activeFor(5L)).isEqualTo(EnumSet.allOf(QuestionOrigin.class));
    }

    @Test
    void activeFor_settingsWithoutAFilterIsEverything() {
        when(repository.findById(5L)).thenReturn(Optional.of(new UserSettings(5L)));

        assertThat(service.activeFor(5L)).isEqualTo(EnumSet.allOf(QuestionOrigin.class));
    }

    @Test
    void activeFor_returnsTheStoredSelection() {
        UserSettings stored = new UserSettings(5L);
        stored.setTestOrigins("JUDGE_PROSECUTOR,NOTARY");
        when(repository.findById(5L)).thenReturn(Optional.of(stored));

        assertThat(service.activeFor(5L)).isEqualTo(EnumSet.of(QuestionOrigin.JUDGE_PROSECUTOR, QuestionOrigin.NOTARY));
    }

    // ---------------------------------------------------------------- zapis

    @Test
    void update_storesTheSelectionForANewUser_createsTheSettingsRow() {
        signedIn();
        when(repository.findById(5L)).thenReturn(Optional.empty());
        when(cardRepository.countTestQuestionsByOrigin()).thenReturn(List.of());

        OriginFilterResponse response = service.update(
                new OriginFilterRequest(List.of(QuestionOrigin.NOTARY, QuestionOrigin.AUTHORED)), authentication);

        ArgumentCaptor<UserSettings> captor = ArgumentCaptor.forClass(UserSettings.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(5L);
        assertThat(captor.getValue().getTestOrigins()).isEqualTo("AUTHORED,NOTARY");
        assertThat(response.selected()).containsExactly(QuestionOrigin.AUTHORED, QuestionOrigin.NOTARY);
        assertThat(response.all()).isFalse();
    }

    @Test
    void update_leavesTheLearningSettingsUntouched() {
        signedIn();
        UserSettings stored = new UserSettings(5L);
        stored.setNewCardsPerDay(15);
        stored.setMaxReviewsPerDay(300);
        stored.setShuffleOptions(false);
        when(repository.findById(5L)).thenReturn(Optional.of(stored));
        when(cardRepository.countTestQuestionsByOrigin()).thenReturn(List.of());

        service.update(new OriginFilterRequest(List.of(QuestionOrigin.BAILIFF)), authentication);

        assertThat(stored.getNewCardsPerDay()).isEqualTo(15);
        assertThat(stored.getMaxReviewsPerDay()).isEqualTo(300);
        assertThat(stored.isShuffleOptions()).isFalse();
        assertThat(stored.getTestOrigins()).isEqualTo("BAILIFF");
    }

    @Test
    void update_selectingEverythingClearsTheColumn() {
        signedIn();
        UserSettings stored = new UserSettings(5L);
        stored.setTestOrigins("NOTARY");
        when(repository.findById(5L)).thenReturn(Optional.of(stored));
        when(cardRepository.countTestQuestionsByOrigin()).thenReturn(List.of());

        OriginFilterResponse response = service.update(
                new OriginFilterRequest(List.of(QuestionOrigin.values())), authentication);

        assertThat(stored.getTestOrigins()).isNull();
        assertThat(response.all()).isTrue();
        assertThat(response.selected()).hasSize(QuestionOrigin.values().length);
    }

    @Test
    void update_repeatedOriginsAreCountedOnce() {
        signedIn();
        when(repository.findById(5L)).thenReturn(Optional.empty());
        when(cardRepository.countTestQuestionsByOrigin()).thenReturn(List.of());

        OriginFilterResponse response = service.update(
                new OriginFilterRequest(List.of(QuestionOrigin.NOTARY, QuestionOrigin.NOTARY)), authentication);

        assertThat(response.selected()).containsExactly(QuestionOrigin.NOTARY);
    }

    @Test
    void update_emptySelectionIsRejected_andNothingIsSaved() {
        signedIn();

        assertThatThrownBy(() -> service.update(new OriginFilterRequest(List.of()), authentication))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("co najmniej jedno");
        verify(repository, never()).save(any());
    }

    @Test
    void update_aNullEntryIsRejected() {
        signedIn();
        List<QuestionOrigin> withNull = new java.util.ArrayList<>();
        withNull.add(QuestionOrigin.NOTARY);
        withNull.add(null);

        assertThatThrownBy(() -> service.update(new OriginFilterRequest(withNull), authentication))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    // ---------------------------------------------------------------- odczyt dla okna wyboru

    @Test
    void get_listsEveryOriginWithItsQuestionCount_zeroWhenThereAreNone() {
        signedIn();
        when(repository.findById(5L)).thenReturn(Optional.empty());
        when(cardRepository.countTestQuestionsByOrigin()).thenReturn(List.of(
                count(QuestionOrigin.AUTHORED, 123), count(QuestionOrigin.NOTARY, 50)));

        OriginFilterResponse response = service.get(authentication);

        assertThat(response.all()).isTrue();
        assertThat(response.selected()).containsExactly(QuestionOrigin.values());
        assertThat(response.options()).hasSize(QuestionOrigin.values().length);
        assertThat(response.options()).filteredOn(o -> o.origin() == QuestionOrigin.AUTHORED)
                .extracting(OriginFilterResponse.Option::questionCount).containsExactly(123L);
        assertThat(response.options()).filteredOn(o -> o.origin() == QuestionOrigin.NOTARY)
                .extracting(OriginFilterResponse.Option::questionCount).containsExactly(50L);
        assertThat(response.options()).filteredOn(o -> o.origin() == QuestionOrigin.BAILIFF)
                .extracting(OriginFilterResponse.Option::questionCount).containsExactly(0L);
    }

    @Test
    void get_reportsAPartialSelection() {
        signedIn();
        UserSettings stored = new UserSettings(5L);
        stored.setTestOrigins("NOTARY");
        when(repository.findById(5L)).thenReturn(Optional.of(stored));
        when(cardRepository.countTestQuestionsByOrigin()).thenReturn(List.of());

        OriginFilterResponse response = service.get(authentication);

        assertThat(response.all()).isFalse();
        assertThat(response.selected()).containsExactly(QuestionOrigin.NOTARY);
    }
}
