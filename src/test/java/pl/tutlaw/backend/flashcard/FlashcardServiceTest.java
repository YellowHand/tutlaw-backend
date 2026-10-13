package pl.tutlaw.backend.flashcard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import pl.tutlaw.backend.card.Card;
import pl.tutlaw.backend.card.CardRepository;
import pl.tutlaw.backend.card.CorrectOption;
import pl.tutlaw.backend.common.exception.ResourceNotFoundException;
import pl.tutlaw.backend.review.ReviewLogRepository;
import pl.tutlaw.backend.review.UserCardStateRepository;
import pl.tutlaw.backend.testsupport.MutableClock;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlashcardServiceTest {

    private static final long USER = 5L;
    private static final long QUESTION = 11L;
    private static final long FLASHCARD = 12L;

    @Mock private CardRepository cardRepository;
    @Mock private FlashcardAdditionRepository additionRepository;
    @Mock private ReviewLogRepository reviewLogRepository;
    @Mock private UserCardStateRepository stateRepository;
    @Mock private CurrentUserResolver currentUserResolver;
    private final Authentication authentication = mock(Authentication.class);
    private final MutableClock clock = MutableClock.at("2026-10-12T10:00:00Z");

    private FlashcardService service;
    private Card question;

    @BeforeEach
    void setUp() {
        service = new FlashcardService(cardRepository, additionRepository, reviewLogRepository, stateRepository,
                currentUserResolver, clock);
        User user = new User();
        user.setId(USER);
        question = new Card();
        question.setId(QUESTION);
        question.setCorrectOption(CorrectOption.A);
        lenient().when(currentUserResolver.resolve(authentication)).thenReturn(user);
    }

    private void questionIsAvailableWithAFlashcard() {
        when(cardRepository.findById(QUESTION)).thenReturn(Optional.of(question));
        when(cardRepository.countReadableById(QUESTION, USER)).thenReturn(1L);
        when(cardRepository.existsByLinkedTestCardIdAndHiddenFalse(QUESTION)).thenReturn(true);
    }

    // ---------------------------------------------------------------- statusOf

    @Test
    void statusOf_withoutAVisibleFlashcard_isUnavailable_whateverElseHappened() {
        when(cardRepository.existsByLinkedTestCardIdAndHiddenFalse(QUESTION)).thenReturn(false);

        assertThat(service.statusOf(USER, QUESTION, true)).isEqualTo(FlashcardStatus.UNAVAILABLE);
        assertThat(service.statusOf(USER, QUESTION, false)).isEqualTo(FlashcardStatus.UNAVAILABLE);
    }

    @Test
    void statusOf_aConfidentMistakeWinsOverTheManualAddition() {
        when(cardRepository.existsByLinkedTestCardIdAndHiddenFalse(QUESTION)).thenReturn(true);

        assertThat(service.statusOf(USER, QUESTION, true)).isEqualTo(FlashcardStatus.ADDED_BY_MISTAKE);
        verify(additionRepository, never()).existsByUserIdAndQuestionId(any(), any());
    }

    @Test
    void statusOf_manualAddition_andNothing() {
        when(cardRepository.existsByLinkedTestCardIdAndHiddenFalse(QUESTION)).thenReturn(true);
        when(additionRepository.existsByUserIdAndQuestionId(USER, QUESTION)).thenReturn(true, false);

        assertThat(service.statusOf(USER, QUESTION, false)).isEqualTo(FlashcardStatus.ADDED_MANUALLY);
        assertThat(service.statusOf(USER, QUESTION, false)).isEqualTo(FlashcardStatus.AVAILABLE);
    }

    // ---------------------------------------------------------------- add

    @Test
    void add_storesTheManualAddition_andReportsIt() {
        questionIsAvailableWithAFlashcard();
        when(reviewLogRepository.countConfidentMistakes(USER, QUESTION)).thenReturn(0L);
        when(additionRepository.existsByUserIdAndQuestionId(USER, QUESTION)).thenReturn(true);

        FlashcardStatus status = service.add(QUESTION, authentication);

        assertThat(status).isEqualTo(FlashcardStatus.ADDED_MANUALLY);
        verify(additionRepository).insertIfAbsent(USER, QUESTION, LocalDateTime.of(2026, 10, 12, 12, 0));
    }

    @Test
    void add_whenTheFlashcardIsAlreadyThereBecauseOfAMistake_storesNothing() {
        questionIsAvailableWithAFlashcard();
        when(reviewLogRepository.countConfidentMistakes(USER, QUESTION)).thenReturn(2L);

        FlashcardStatus status = service.add(QUESTION, authentication);

        assertThat(status).isEqualTo(FlashcardStatus.ADDED_BY_MISTAKE);
        verify(additionRepository, never()).insertIfAbsent(any(), any(), any());
    }

    @Test
    void add_isIdempotent_theInsertIsAskedEveryTimeAndTheDatabaseIgnoresTheRepeat() {
        questionIsAvailableWithAFlashcard();
        when(reviewLogRepository.countConfidentMistakes(USER, QUESTION)).thenReturn(0L);
        when(additionRepository.existsByUserIdAndQuestionId(USER, QUESTION)).thenReturn(true);

        service.add(QUESTION, authentication);
        service.add(QUESTION, authentication);

        verify(additionRepository, org.mockito.Mockito.times(2)).insertIfAbsent(any(), any(), any());
    }

    @Test
    void add_unknownQuestion_isNotFound() {
        when(cardRepository.findById(QUESTION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.add(QUESTION, authentication)).isInstanceOf(ResourceNotFoundException.class);
        verify(additionRepository, never()).insertIfAbsent(any(), any(), any());
    }

    @Test
    void add_aCardThatIsNotATestQuestion_isNotFound() {
        Card flashcard = new Card();
        flashcard.setId(QUESTION);
        when(cardRepository.findById(QUESTION)).thenReturn(Optional.of(flashcard));

        assertThatThrownBy(() -> service.add(QUESTION, authentication)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void add_hiddenQuestion_isNotFound() {
        question.setHidden(true);
        when(cardRepository.findById(QUESTION)).thenReturn(Optional.of(question));

        assertThatThrownBy(() -> service.add(QUESTION, authentication)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void add_questionTheUserCannotReachYet_isNotFound() {
        when(cardRepository.findById(QUESTION)).thenReturn(Optional.of(question));
        when(cardRepository.countReadableById(QUESTION, USER)).thenReturn(0L);

        assertThatThrownBy(() -> service.add(QUESTION, authentication)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void add_questionWithoutAVisibleFlashcard_isRejected() {
        when(cardRepository.findById(QUESTION)).thenReturn(Optional.of(question));
        when(cardRepository.countReadableById(QUESTION, USER)).thenReturn(1L);
        when(cardRepository.existsByLinkedTestCardIdAndHiddenFalse(QUESTION)).thenReturn(false);

        assertThatThrownBy(() -> service.add(QUESTION, authentication))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nie ma fiszki");
        verify(additionRepository, never()).insertIfAbsent(any(), any(), any());
    }

    // ---------------------------------------------------------------- remove

    @Test
    void remove_undoesTheManualAddition_andForgetsTheFlashcardSchedule() {
        when(cardRepository.findById(QUESTION)).thenReturn(Optional.of(question));
        when(reviewLogRepository.countConfidentMistakes(USER, QUESTION)).thenReturn(0L);
        when(cardRepository.findIdsByLinkedTestCardId(QUESTION)).thenReturn(List.of(FLASHCARD));
        when(cardRepository.existsByLinkedTestCardIdAndHiddenFalse(QUESTION)).thenReturn(true);
        when(additionRepository.existsByUserIdAndQuestionId(USER, QUESTION)).thenReturn(false);

        FlashcardStatus status = service.remove(QUESTION, authentication);

        assertThat(status).isEqualTo(FlashcardStatus.AVAILABLE);
        verify(additionRepository).deleteByUserIdAndQuestionId(USER, QUESTION);
        verify(stateRepository).deleteByUserIdAndCardIds(USER, List.of(FLASHCARD));
    }

    @Test
    void remove_whenAConfidentMistakeKeepsTheFlashcardInFiszki_keepsItsSchedule() {
        when(cardRepository.findById(QUESTION)).thenReturn(Optional.of(question));
        when(reviewLogRepository.countConfidentMistakes(USER, QUESTION)).thenReturn(1L);
        when(cardRepository.existsByLinkedTestCardIdAndHiddenFalse(QUESTION)).thenReturn(true);

        FlashcardStatus status = service.remove(QUESTION, authentication);

        assertThat(status).isEqualTo(FlashcardStatus.ADDED_BY_MISTAKE);
        verify(additionRepository).deleteByUserIdAndQuestionId(USER, QUESTION);
        verify(stateRepository, never()).deleteByUserIdAndCardIds(any(), any());
    }

    @Test
    void remove_whenThereWasNothingToRemove_isHarmless() {
        when(cardRepository.findById(QUESTION)).thenReturn(Optional.of(question));
        when(reviewLogRepository.countConfidentMistakes(USER, QUESTION)).thenReturn(0L);
        when(cardRepository.findIdsByLinkedTestCardId(QUESTION)).thenReturn(List.of());
        when(cardRepository.existsByLinkedTestCardIdAndHiddenFalse(QUESTION)).thenReturn(true);
        when(additionRepository.existsByUserIdAndQuestionId(USER, QUESTION)).thenReturn(false);

        assertThat(service.remove(QUESTION, authentication)).isEqualTo(FlashcardStatus.AVAILABLE);
        verify(stateRepository, never()).deleteByUserIdAndCardIds(any(), any());
    }

    @Test
    void remove_unknownQuestion_isNotFound() {
        when(cardRepository.findById(QUESTION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remove(QUESTION, authentication)).isInstanceOf(ResourceNotFoundException.class);
        verify(additionRepository, never()).deleteByUserIdAndQuestionId(any(), any());
    }
}
