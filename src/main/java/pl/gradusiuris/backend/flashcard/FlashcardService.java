package pl.gradusiuris.backend.flashcard;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gradusiuris.backend.common.exception.ResourceNotFoundException;
import pl.gradusiuris.backend.question.Question;
import pl.gradusiuris.backend.question.QuestionRepository;
import pl.gradusiuris.backend.review.ReviewLogRepository;
import pl.gradusiuris.backend.review.ReviewMode;
import pl.gradusiuris.backend.review.UserQuestionStateRepository;
import pl.gradusiuris.backend.user.CurrentUserResolver;
import pl.gradusiuris.backend.user.User;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Ręczne dodawanie pytania do fiszek (po odpowiedzi w teście) i wycofywanie tego dodania. Fiszka to tryb nauki tego
 * samego pytania (nie osobny rekord), więc tu decydujemy tylko, czy użytkownik ją widzi: pytanie ma fiszkę, dopóki
 * administrator jej nie ukryje ({@code flashcardHidden}).
 */
@Service
@RequiredArgsConstructor
public class FlashcardService {

    private final QuestionRepository questionRepository;
    private final FlashcardAdditionRepository additionRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final UserQuestionStateRepository stateRepository;
    private final CurrentUserResolver currentUserResolver;
    private final Clock clock;

    /**
     * Stan fiszki pytania dla użytkownika. {@code confidentMistake} mówi, że użytkownik ma już pewny błąd w tym
     * pytaniu (wtedy fiszka jest w Fiszkach niezależnie od ręcznego dodania).
     */
    public FlashcardStatus statusOf(Long userId, Question question, boolean confidentMistake) {
        if (question.isFlashcardHidden()) {
            return FlashcardStatus.UNAVAILABLE;
        }
        if (confidentMistake) {
            return FlashcardStatus.ADDED_BY_MISTAKE;
        }
        if (additionRepository.existsByUserIdAndQuestionId(userId, question.getId())) {
            return FlashcardStatus.ADDED_MANUALLY;
        }
        return FlashcardStatus.AVAILABLE;
    }

    /** „Dodaj do fiszek": fiszka pytania trafia do Fiszek użytkownika jako nowa. Powtórne dodanie nic nie psuje. */
    @Transactional
    public FlashcardStatus add(Long questionId, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        Question question = requireQuestion(questionId);
        if (question.isHidden()) {
            throw new ResourceNotFoundException("Question not found");
        }
        if (question.isFlashcardHidden()) {
            throw new IllegalArgumentException("Z tego pytania nie ma fiszki.");
        }
        boolean byMistake = reviewLogRepository.countConfidentMistakes(user.getId(), questionId) > 0;
        if (!byMistake) {
            additionRepository.insertIfAbsent(user.getId(), questionId, LocalDateTime.now(clock));
        }
        return statusOf(user.getId(), question, byMistake);
    }

    /**
     * „Usuń z fiszek": cofa ręczne dodanie. Fiszka znika z Fiszek, a jej dotychczasowy harmonogram nauki jest kasowany
     * (ponowne dodanie zaczyna ją od nowa). Jeśli fiszka jest w Fiszkach z powodu pewnego błędu, zostaje, bo tego
     * nie da się cofnąć.
     */
    @Transactional
    public FlashcardStatus remove(Long questionId, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        Question question = requireQuestion(questionId);
        boolean byMistake = reviewLogRepository.countConfidentMistakes(user.getId(), questionId) > 0;
        additionRepository.deleteByUserIdAndQuestionId(user.getId(), questionId);
        if (!byMistake) {
            stateRepository.deleteByUserIdAndModeAndQuestionIds(
                    user.getId(), ReviewMode.FLASHCARD, List.of(questionId));
        }
        return statusOf(user.getId(), question, byMistake);
    }

    private Question requireQuestion(Long questionId) {
        return questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found"));
    }
}
