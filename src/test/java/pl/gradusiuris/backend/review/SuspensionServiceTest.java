package pl.gradusiuris.backend.review;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import pl.gradusiuris.backend.common.exception.ResourceNotFoundException;
import pl.gradusiuris.backend.review.dto.DifficultQuestionsResponse;
import pl.gradusiuris.backend.testsupport.MutableClock;
import pl.gradusiuris.backend.testsupport.Pools;
import pl.gradusiuris.backend.user.CurrentUserResolver;
import pl.gradusiuris.backend.user.User;

import java.time.LocalDateTime;
import java.util.Collection;
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

    @Mock private UserQuestionStateRepository stateRepository;
    @Mock private CurrentUserResolver currentUserResolver;
    private final Authentication authentication = mock(Authentication.class);

    private SuspensionService service;

    @BeforeEach
    void setUp() {
        service = new SuspensionService(stateRepository, currentUserResolver, Pools.holding(Pools::all),
                new LeechPolicy(8), clock);
        User user = new User();
        user.setId(5L);
        when(currentUserResolver.resolve(authentication)).thenReturn(user);
    }

    private static UserQuestionState state(boolean suspended) {
        UserQuestionState state = new UserQuestionState();
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

    private static UserQuestionStateRepository.DifficultRow row(
            long questionId, ReviewMode mode, String body, int lapses, LocalDateTime suspendedAt) {
        return new UserQuestionStateRepository.DifficultRow() {
            public Long getQuestionId() { return questionId; }
            public ReviewMode getMode() { return mode; }
            public String getBody() { return body; }
            public String getActName() { return "Kodeks cywilny"; }
            public Integer getLapses() { return lapses; }
            public LocalDateTime getSuspendedAt() { return suspendedAt; }
        };
    }

    // ---------------------------------------------------------------- zawieszanie

    @Test
    void suspend_setsTheFlagAndTime_andKeepsTheLearningProgress() {
        UserQuestionState state = state(false);
        when(stateRepository.findByUserIdAndQuestionIdAndMode(5L, 11L, ReviewMode.TEST)).thenReturn(Optional.of(state));

        service.suspend(11L, ReviewMode.TEST, authentication);

        assertThat(state.isSuspended()).isTrue();
        assertThat(state.getSuspendedAt()).isEqualTo(NOW);
        assertThat(state.getReps()).isEqualTo(4);
        assertThat(state.getLapses()).isEqualTo(9);
        assertThat(state.getStability()).isEqualTo(12.0);
        assertThat(state.getDueDate()).isEqualTo(NOW.minusDays(2));
        verify(stateRepository).save(state);
    }

    @Test
    void suspend_isPerMode_theOtherModeOfTheSameQuestionIsUntouched() {
        UserQuestionState test = state(false);
        when(stateRepository.findByUserIdAndQuestionIdAndMode(5L, 11L, ReviewMode.TEST)).thenReturn(Optional.of(test));

        service.suspend(11L, ReviewMode.TEST, authentication);

        verify(stateRepository, never()).findByUserIdAndQuestionIdAndMode(5L, 11L, ReviewMode.FLASHCARD);
    }

    @Test
    void suspend_twice_changesNothingAndDoesNotWrite() {
        UserQuestionState state = state(true);
        when(stateRepository.findByUserIdAndQuestionIdAndMode(5L, 11L, ReviewMode.FLASHCARD)).thenReturn(Optional.of(state));

        service.suspend(11L, ReviewMode.FLASHCARD, authentication);

        assertThat(state.getSuspendedAt()).as("data pierwszego zawieszenia zostaje").isEqualTo(NOW.minusDays(3));
        verify(stateRepository, never()).save(any());
    }

    @Test
    void suspend_aQuestionTheUserNeverAnsweredInThatMode_isTreatedAsMissing() {
        when(stateRepository.findByUserIdAndQuestionIdAndMode(5L, 99L, ReviewMode.TEST)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.suspend(99L, ReviewMode.TEST, authentication))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(stateRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- przywracanie

    @Test
    void unsuspend_clearsTheFlag_andLeavesTheDueDateAlone() {
        UserQuestionState state = state(true);
        when(stateRepository.findByUserIdAndQuestionIdAndMode(5L, 11L, ReviewMode.TEST)).thenReturn(Optional.of(state));

        service.unsuspend(11L, ReviewMode.TEST, authentication);

        assertThat(state.isSuspended()).isFalse();
        assertThat(state.getSuspendedAt()).isNull();
        assertThat(state.getDueDate()).isEqualTo(NOW.minusDays(2));
        verify(stateRepository).save(state);
    }

    @Test
    void unsuspend_aQuestionThatIsNotSuspended_isANoOp() {
        UserQuestionState state = state(false);
        when(stateRepository.findByUserIdAndQuestionIdAndMode(5L, 11L, ReviewMode.TEST)).thenReturn(Optional.of(state));

        service.unsuspend(11L, ReviewMode.TEST, authentication);

        verify(stateRepository, never()).save(any());
    }

    @Test
    void unsuspend_unknownQuestion_isTreatedAsMissing() {
        when(stateRepository.findByUserIdAndQuestionIdAndMode(5L, 99L, ReviewMode.FLASHCARD)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unsuspend(99L, ReviewMode.FLASHCARD, authentication))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------------------------------------------------------------- lista

    @Test
    void list_returnsSuspendedAndDifficultQuestions_usingTheConfiguredThreshold() {
        when(stateRepository.findSuspendedRows(eq(5L), ArgumentMatchers.<Pageable>any()))
                .thenReturn(List.of(row(1, ReviewMode.TEST, "Zawieszone?", 9, NOW.minusDays(1))));
        when(stateRepository.findLeechRows(eq(5L), eq(8), ArgumentMatchers.<Collection<Long>>any(),
                ArgumentMatchers.<Pageable>any()))
                .thenReturn(List.of(row(2, ReviewMode.TEST, "Trudne?", 12, null),
                        row(3, ReviewMode.FLASHCARD, "Trochę trudne?", 8, null)));

        DifficultQuestionsResponse response = service.list(authentication);

        assertThat(response.leechThreshold()).isEqualTo(8);
        assertThat(response.suspended()).extracting(DifficultQuestionsResponse.Item::questionId).containsExactly(1L);
        assertThat(response.suspended().get(0).suspendedAt()).isEqualTo(NOW.minusDays(1));
        assertThat(response.suspended().get(0).mode()).isEqualTo(ReviewMode.TEST);
        assertThat(response.suspended().get(0).actName()).isEqualTo("Kodeks cywilny");
        assertThat(response.leeches()).extracting(DifficultQuestionsResponse.Item::questionId).containsExactly(2L, 3L);
        assertThat(response.leeches().get(0).lapses()).isEqualTo(12);
        assertThat(response.leeches().get(0).suspendedAt()).isNull();
        assertThat(response.leeches().get(1).mode()).isEqualTo(ReviewMode.FLASHCARD);
    }

    @Test
    void list_looksForDifficultQuestionsOnlyInTheUsersPool() {
        when(stateRepository.findSuspendedRows(eq(5L), ArgumentMatchers.<Pageable>any())).thenReturn(List.of());
        when(stateRepository.findLeechRows(eq(5L), eq(8), ArgumentMatchers.<Collection<Long>>any(),
                ArgumentMatchers.<Pageable>any())).thenReturn(List.of());

        service.list(authentication);

        verify(stateRepository).findLeechRows(eq(5L), eq(8), eq(Pools.all().actIds()), ArgumentMatchers.<Pageable>any());
    }

    @Test
    void list_withNothingToShow_returnsEmptyLists() {
        when(stateRepository.findSuspendedRows(eq(5L), ArgumentMatchers.<Pageable>any())).thenReturn(List.of());
        when(stateRepository.findLeechRows(eq(5L), eq(8), ArgumentMatchers.<Collection<Long>>any(),
                ArgumentMatchers.<Pageable>any())).thenReturn(List.of());

        DifficultQuestionsResponse response = service.list(authentication);

        assertThat(response.suspended()).isEmpty();
        assertThat(response.leeches()).isEmpty();
    }
}
