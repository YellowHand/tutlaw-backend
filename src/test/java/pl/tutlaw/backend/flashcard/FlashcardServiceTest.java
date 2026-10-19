package pl.tutlaw.backend.flashcard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import pl.tutlaw.backend.common.exception.ResourceNotFoundException;
import pl.tutlaw.backend.question.Question;
import pl.tutlaw.backend.question.QuestionRepository;
import pl.tutlaw.backend.review.ReviewLogRepository;
import pl.tutlaw.backend.review.ReviewMode;
import pl.tutlaw.backend.review.UserQuestionStateRepository;
import pl.tutlaw.backend.testsupport.MutableClock;
import pl.tutlaw.backend.testsupport.QuestionRefs;
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

    @Mock private QuestionRepository questionRepository;
    @Mock private FlashcardAdditionRepository additionRepository;
    @Mock private ReviewLogRepository reviewLogRepository;
    @Mock private UserQuestionStateRepository stateRepository;
    @Mock private CurrentUserResolver currentUserResolver;
    private final Authentication authentication = mock(Authentication.class);
    private final MutableClock clock = MutableClock.at("2026-10-12T10:00:00Z");

    private FlashcardService service;
    private Question question;

    @BeforeEach
    void setUp() {
        service = new FlashcardService(questionRepository, additionRepository, reviewLogRepository, stateRepository,
                currentUserResolver, clock);
        User user = new User();
        user.setId(USER);
        question = QuestionRefs.question(QUESTION);
        lenient().when(currentUserResolver.resolve(authentication)).thenReturn(user);
    }

    private void questionExists() {
        when(questionRepository.findById(QUESTION)).thenReturn(Optional.of(question));
    }

    // ---------------------------------------------------------------- statusOf

    @Test
    void statusOf_withAHiddenFlashcard_isUnavailable_whateverElseHappened() {
        question.setFlashcardHidden(true);

        assertThat(service.statusOf(USER, question, true)).isEqualTo(FlashcardStatus.UNAVAILABLE);
        assertThat(service.statusOf(USER, question, false)).isEqualTo(FlashcardStatus.UNAVAILABLE);
    }

    @Test
    void statusOf_aConfidentMistakeWinsOverTheManualAddition() {
        assertThat(service.statusOf(USER, question, true)).isEqualTo(FlashcardStatus.ADDED_BY_MISTAKE);
        verify(additionRepository, never()).existsByUserIdAndQuestionId(any(), any());
    }

    @Test
    void statusOf_manualAddition_andNothing() {
        when(additionRepository.existsByUserIdAndQuestionId(USER, QUESTION)).thenReturn(true, false);

        assertThat(service.statusOf(USER, question, false)).isEqualTo(FlashcardStatus.ADDED_MANUALLY);
        assertThat(service.statusOf(USER, question, false)).isEqualTo(FlashcardStatus.AVAILABLE);
    }

    // ---------------------------------------------------------------- add

    @Test
    void add_storesTheManualAddition_andReportsIt() {
        questionExists();
        when(reviewLogRepository.countConfidentMistakes(USER, QUESTION)).thenReturn(0L);
        when(additionRepository.existsByUserIdAndQuestionId(USER, QUESTION)).thenReturn(true);

        FlashcardStatus status = service.add(QUESTION, authentication);

        assertThat(status).isEqualTo(FlashcardStatus.ADDED_MANUALLY);
        verify(additionRepository).insertIfAbsent(USER, QUESTION, LocalDateTime.of(2026, 10, 12, 12, 0));
    }

    @Test
    void add_whenTheFlashcardIsAlreadyThereBecauseOfAMistake_storesNothing() {
        questionExists();
        when(reviewLogRepository.countConfidentMistakes(USER, QUESTION)).thenReturn(2L);

        FlashcardStatus status = service.add(QUESTION, authentication);

        assertThat(status).isEqualTo(FlashcardStatus.ADDED_BY_MISTAKE);
        verify(additionRepository, never()).insertIfAbsent(any(), any(), any());
    }

    @Test
    void add_isIdempotent_theInsertIsAskedEveryTimeAndTheDatabaseIgnoresTheRepeat() {
        questionExists();
        when(reviewLogRepository.countConfidentMistakes(USER, QUESTION)).thenReturn(0L);
        when(additionRepository.existsByUserIdAndQuestionId(USER, QUESTION)).thenReturn(true);

        service.add(QUESTION, authentication);
        service.add(QUESTION, authentication);

        verify(additionRepository, org.mockito.Mockito.times(2)).insertIfAbsent(any(), any(), any());
    }

    @Test
    void add_unknownQuestion_isNotFound() {
        when(questionRepository.findById(QUESTION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.add(QUESTION, authentication)).isInstanceOf(ResourceNotFoundException.class);
        verify(additionRepository, never()).insertIfAbsent(any(), any(), any());
    }

    @Test
    void add_hiddenQuestion_isNotFound() {
        question.setHidden(true);
        questionExists();

        assertThatThrownBy(() -> service.add(QUESTION, authentication)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void add_questionWithAHiddenFlashcard_isRejected() {
        question.setFlashcardHidden(true);
        questionExists();

        assertThatThrownBy(() -> service.add(QUESTION, authentication))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nie ma fiszki");
        verify(additionRepository, never()).insertIfAbsent(any(), any(), any());
    }

    // ---------------------------------------------------------------- remove

    @Test
    void remove_undoesTheManualAddition_andForgetsOnlyTheFlashcardSchedule() {
        questionExists();
        when(reviewLogRepository.countConfidentMistakes(USER, QUESTION)).thenReturn(0L);
        when(additionRepository.existsByUserIdAndQuestionId(USER, QUESTION)).thenReturn(false);

        FlashcardStatus status = service.remove(QUESTION, authentication);

        assertThat(status).isEqualTo(FlashcardStatus.AVAILABLE);
        verify(additionRepository).deleteByUserIdAndQuestionId(USER, QUESTION);
        // harmonogram TESTU tego pytania zostaje: kasujemy tylko stan trybu FLASHCARD
        verify(stateRepository).deleteByUserIdAndModeAndQuestionIds(USER, ReviewMode.FLASHCARD, List.of(QUESTION));
        verify(stateRepository, never()).deleteByUserIdAndModeAndQuestionIds(any(), org.mockito.ArgumentMatchers.eq(ReviewMode.TEST), any());
    }

    @Test
    void remove_whenAConfidentMistakeKeepsTheFlashcardInFiszki_keepsItsSchedule() {
        questionExists();
        when(reviewLogRepository.countConfidentMistakes(USER, QUESTION)).thenReturn(1L);

        FlashcardStatus status = service.remove(QUESTION, authentication);

        assertThat(status).isEqualTo(FlashcardStatus.ADDED_BY_MISTAKE);
        verify(additionRepository).deleteByUserIdAndQuestionId(USER, QUESTION);
        verify(stateRepository, never()).deleteByUserIdAndModeAndQuestionIds(any(), any(), any());
    }

    @Test
    void remove_unknownQuestion_isNotFound() {
        when(questionRepository.findById(QUESTION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remove(QUESTION, authentication)).isInstanceOf(ResourceNotFoundException.class);
        verify(additionRepository, never()).deleteByUserIdAndQuestionId(any(), any());
    }
}
