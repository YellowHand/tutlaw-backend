package pl.tutlaw.backend.settings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import pl.tutlaw.backend.review.ReviewLogRepository;
import pl.tutlaw.backend.review.ReviewMode;
import pl.tutlaw.backend.settings.dto.InterfaceSettingsResponse;
import pl.tutlaw.backend.settings.dto.UpdateInterfaceRequest;
import pl.tutlaw.backend.testsupport.FakeRepos;
import pl.tutlaw.backend.testsupport.MutableClock;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InterfaceSettingsServiceTest {

    private final MutableClock clock = MutableClock.at("2026-10-12T10:00:00Z");
    private final Authentication authentication = mock(Authentication.class);
    private final Map<Long, UserSettings> store = new HashMap<>();
    /** Ile odpowiedzi ma użytkownik 5 w każdym trybie. */
    private final Map<ReviewMode, Integer> answers = new HashMap<>(Map.of(ReviewMode.TEST, 0, ReviewMode.FLASHCARD, 0));
    private InterfaceSettingsService service;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(5L);
        CurrentUserResolver resolver = mock(CurrentUserResolver.class);
        when(resolver.resolve(authentication)).thenReturn(user);
        UserSettingsRepository settings = FakeRepos.proxy(UserSettingsRepository.class, (method, args) -> {
            switch (method) {
                case "findById":
                    return Optional.ofNullable(store.get((Long) args[0]));
                case "save": {
                    UserSettings s = (UserSettings) args[0];
                    store.put(s.getUserId(), s);
                    return s;
                }
                default:
                    throw new UnsupportedOperationException("UserSettingsRepository." + method);
            }
        });
        ReviewLogRepository logs = FakeRepos.proxy(ReviewLogRepository.class, (method, args) -> {
            assertThat(method).isEqualTo("findIdsByMode");
            int total = answers.get((ReviewMode) args[1]);
            int pageSize = ((Pageable) args[2]).getPageSize();
            List<Long> ids = new ArrayList<>();
            for (long i = 1; i <= Math.min(total, pageSize); i++) {
                ids.add(i);
            }
            return ids;
        });
        service = new InterfaceSettingsService(settings, logs, resolver, clock);
    }

    @Test
    void aNewAccountSeesEverythingAndHasNoAnswers() {
        InterfaceSettingsResponse r = service.get(authentication);

        assertThat(r.testHintHidden()).isFalse();
        assertThat(r.flashcardHintHidden()).isFalse();
        assertThat(r.questionTimerHidden()).isFalse();
        assertThat(r.testAnswers()).isZero();
        assertThat(r.flashcardReviews()).isZero();
        assertThat(store).isEmpty(); // odczyt niczego nie zapisuje
    }

    @Test
    void answersAreCountedPerModeAndStopAtTheCap() {
        answers.put(ReviewMode.TEST, 4);
        answers.put(ReviewMode.FLASHCARD, 8000);

        InterfaceSettingsResponse r = service.get(authentication);

        assertThat(r.testAnswers()).isEqualTo(4);
        assertThat(r.flashcardReviews()).isEqualTo(InterfaceSettingsService.ANSWERS_CAP).isEqualTo(10);
    }

    @Test
    void updateChangesOnlyTheGivenFields() {
        service.update(new UpdateInterfaceRequest(true, null, null), authentication);
        InterfaceSettingsResponse r = service.update(new UpdateInterfaceRequest(null, null, true), authentication);

        assertThat(r.testHintHidden()).isTrue();      // z pierwszego zapisu
        assertThat(r.flashcardHintHidden()).isFalse();
        assertThat(r.questionTimerHidden()).isTrue();
        assertThat(store.get(5L).getUpdatedAt()).isNotNull();
    }

    @Test
    void theHintCanBeShownAgain() {
        service.update(new UpdateInterfaceRequest(null, true, null), authentication);

        InterfaceSettingsResponse r = service.update(new UpdateInterfaceRequest(null, false, null), authentication);

        assertThat(r.flashcardHintHidden()).isFalse();
    }

    @Test
    void updateKeepsOtherSettingsOfTheAccount() {
        UserSettings existing = new UserSettings(5L);
        existing.setNewCardsPerDay(7);
        existing.setTheme(Theme.LIGHT);
        store.put(5L, existing);

        service.update(new UpdateInterfaceRequest(true, true, true), authentication);

        assertThat(existing.getNewCardsPerDay()).isEqualTo(7);
        assertThat(existing.getTheme()).isEqualTo(Theme.LIGHT);
        assertThat(existing.isHintHiddenTest()).isTrue();
    }
}
