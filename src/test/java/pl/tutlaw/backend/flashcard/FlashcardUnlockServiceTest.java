package pl.tutlaw.backend.flashcard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.tutlaw.backend.review.ReviewLogRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlashcardUnlockServiceTest {

    @Mock private ReviewLogRepository reviewLogRepository;
    @Mock private FlashcardAdditionRepository additionRepository;

    @Test
    void unlockedQuestions_areConfidentMistakesPlusManualAdditions_withoutDuplicates() {
        when(reviewLogRepository.findConfidentMistakeCardIds(5L)).thenReturn(List.of(1L, 2L));
        when(additionRepository.findQuestionIdsByUserId(5L)).thenReturn(List.of(2L, 3L));

        assertThat(new FlashcardUnlockService(reviewLogRepository, additionRepository).unlockedQuestionIds(5L))
                .containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void unlockedQuestions_isEmptyForANewAccount() {
        when(reviewLogRepository.findConfidentMistakeCardIds(5L)).thenReturn(List.of());
        when(additionRepository.findQuestionIdsByUserId(5L)).thenReturn(List.of());

        assertThat(new FlashcardUnlockService(reviewLogRepository, additionRepository).unlockedQuestionIds(5L)).isEmpty();
    }
}
