package pl.tutlaw.backend.review;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import pl.tutlaw.backend.common.exception.ResourceNotFoundException;
import pl.tutlaw.backend.review.dto.DifficultCardsResponse;
import pl.tutlaw.backend.testsupport.MutableClock;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.time.LocalDateTime;
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
class SuspensionServiceTest {

    // 2026-10-12 12:00 czasu warszawskiego
    private final MutableClock clock = MutableClock.at("2026-10-12T10:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 12, 0);

    @Mock private UserCardStateRepository stateRepository;
    @Mock private CurrentUserResolver currentUserResolver;
    private final Authentication authentication = mock(Authentication.class);

    private SuspensionService service;

    @BeforeEach
    void setUp() {
        service = new SuspensionService(stateRepository, currentUserResolver, new LeechPolicy(8), clock);
        User user = new User();
        user.setId(5L);
        when(currentUserResolver.resolve(authentication)).thenReturn(user);
    }

    private static UserCardState state(boolean suspended) {
        UserCardState state = new UserCardState();
        state.setReps(4);
        state.setLapses(9);
        state.setStability(12.0);
        state.setDueDate(NOW.minusDays(2));
        if (suspended) {
            state.setSuspended(true);
            state.setSuspendedAt(NOW.minusDays(3));
        }
        return state;
    }

    private static UserCardStateRepository.DifficultRow row(long cardId, String front, int lapses, LocalDateTime suspendedAt) {
        return new UserCardStateRepository.DifficultRow() {
            public Long getCardId() { return cardId; }
            public String getFront() { return front; }
            public String getSourceName() { return "Egzamin"; }
            public Integer getLapses() { return lapses; }
            public LocalDateTime getSuspendedAt() { return suspendedAt; }
        };
    }

    // ---------------------------------------------------------------- zawieszanie

    @Test
    void suspend_setsTheFlagAndTime_andKeepsTheLearningProgress() {
        UserCardState state = state(false);
        when(stateRepository.findByUserIdAndCardId(5L, 11L)).thenReturn(Optional.of(state));

        service.suspend(11L, authentication);

        assertThat(state.isSuspended()).isTrue();
        assertThat(state.getSuspendedAt()).isEqualTo(NOW);
        assertThat(state.getReps()).isEqualTo(4);
        assertThat(state.getLapses()).isEqualTo(9);
        assertThat(state.getStability()).isEqualTo(12.0);
        assertThat(state.getDueDate()).isEqualTo(NOW.minusDays(2));
        verify(stateRepository).save(state);
    }

    @Test
    void suspend_twice_changesNothingAndDoesNotWrite() {
        UserCardState state = state(true);
        when(stateRepository.findByUserIdAndCardId(5L, 11L)).thenReturn(Optional.of(state));

        service.suspend(11L, authentication);

        assertThat(state.getSuspendedAt()).as("data pierwszego zawieszenia zostaje").isEqualTo(NOW.minusDays(3));
        verify(stateRepository, never()).save(any());
    }

    @Test
    void suspend_aQuestionTheUserNeverAnswered_isTreatedAsMissing() {
        when(stateRepository.findByUserIdAndCardId(5L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.suspend(99L, authentication)).isInstanceOf(ResourceNotFoundException.class);
        verify(stateRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- przywracanie

    @Test
    void unsuspend_clearsTheFlag_andLeavesTheDueDateAlone() {
        UserCardState state = state(true);
        when(stateRepository.findByUserIdAndCardId(5L, 11L)).thenReturn(Optional.of(state));

        service.unsuspend(11L, authentication);

        assertThat(state.isSuspended()).isFalse();
        assertThat(state.getSuspendedAt()).isNull();
        assertThat(state.getDueDate()).isEqualTo(NOW.minusDays(2));
        verify(stateRepository).save(state);
    }

    @Test
    void unsuspend_aQuestionThatIsNotSuspended_isANoOp() {
        UserCardState state = state(false);
        when(stateRepository.findByUserIdAndCardId(5L, 11L)).thenReturn(Optional.of(state));

        service.unsuspend(11L, authentication);

        verify(stateRepository, never()).save(any());
    }

    @Test
    void unsuspend_unknownQuestion_isTreatedAsMissing() {
        when(stateRepository.findByUserIdAndCardId(5L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unsuspend(99L, authentication)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------------------------------------------------------------- lista

    @Test
    void list_returnsSuspendedAndDifficultQuestions_usingTheConfiguredThreshold() {
        when(stateRepository.findSuspendedRows(eq(5L), org.mockito.ArgumentMatchers.<Pageable>any()))
                .thenReturn(List.of(row(1, "Zawieszone?", 9, NOW.minusDays(1))));
        when(stateRepository.findLeechRows(eq(5L), eq(8), org.mockito.ArgumentMatchers.<Pageable>any()))
                .thenReturn(List.of(row(2, "Trudne?", 12, null), row(3, "Trochę trudne?", 8, null)));

        DifficultCardsResponse response = service.list(authentication);

        assertThat(response.leechThreshold()).isEqualTo(8);
        assertThat(response.suspended()).extracting(DifficultCardsResponse.Item::cardId).containsExactly(1L);
        assertThat(response.suspended().get(0).suspendedAt()).isEqualTo(NOW.minusDays(1));
        assertThat(response.leeches()).extracting(DifficultCardsResponse.Item::cardId).containsExactly(2L, 3L);
        assertThat(response.leeches().get(0).lapses()).isEqualTo(12);
        assertThat(response.leeches().get(0).suspendedAt()).isNull();
    }

    @Test
    void list_withNothingToShow_returnsEmptyLists() {
        when(stateRepository.findSuspendedRows(eq(5L), org.mockito.ArgumentMatchers.<Pageable>any())).thenReturn(List.of());
        when(stateRepository.findLeechRows(eq(5L), eq(8), org.mockito.ArgumentMatchers.<Pageable>any())).thenReturn(List.of());

        DifficultCardsResponse response = service.list(authentication);

        assertThat(response.suspended()).isEmpty();
        assertThat(response.leeches()).isEmpty();
    }
}
