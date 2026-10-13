package pl.tutlaw.backend.flashcard;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tutlaw.backend.card.Card;
import pl.tutlaw.backend.card.CardRepository;
import pl.tutlaw.backend.common.exception.ResourceNotFoundException;
import pl.tutlaw.backend.review.ReviewLogRepository;
import pl.tutlaw.backend.review.UserCardStateRepository;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Ręczne dodawanie pytania do fiszek (po odpowiedzi w teście) i wycofywanie tego dodania.
 * Sama fiszka istnieje zawsze (tworzy ją wyzwalacz w bazie); tu decydujemy tylko, czy użytkownik ją widzi.
 */
@Service
@RequiredArgsConstructor
public class FlashcardService {

    private final CardRepository cardRepository;
    private final FlashcardAdditionRepository additionRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final UserCardStateRepository stateRepository;
    private final CurrentUserResolver currentUserResolver;
    private final Clock clock;

    /**
     * Stan fiszki pytania dla użytkownika. {@code confidentMistake} mówi, że użytkownik ma już pewny błąd w tym
     * pytaniu (wtedy fiszka jest w Fiszkach niezależnie od ręcznego dodania).
     */
    public FlashcardStatus statusOf(Long userId, Long questionId, boolean confidentMistake) {
        if (!cardRepository.existsByLinkedTestCardIdAndHiddenFalse(questionId)) {
            return FlashcardStatus.UNAVAILABLE;
        }
        if (confidentMistake) {
            return FlashcardStatus.ADDED_BY_MISTAKE;
        }
        if (additionRepository.existsByUserIdAndQuestionId(userId, questionId)) {
            return FlashcardStatus.ADDED_MANUALLY;
        }
        return FlashcardStatus.AVAILABLE;
    }

    /** „Dodaj do fiszek": fiszka pytania trafia do Fiszek użytkownika jako nowa karta. Powtórne dodanie nic nie psuje. */
    @Transactional
    public FlashcardStatus add(Long questionId, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        requireQuestionWithFlashcard(questionId, user);
        boolean byMistake = reviewLogRepository.countConfidentMistakes(user.getId(), questionId) > 0;
        if (!byMistake) {
            additionRepository.insertIfAbsent(user.getId(), questionId, LocalDateTime.now(clock));
        }
        return statusOf(user.getId(), questionId, byMistake);
    }

    /**
     * „Usuń z fiszek": cofa ręczne dodanie. Fiszka znika z Fiszek, a jej dotychczasowy harmonogram nauki jest kasowany
     * (ponowne dodanie zaczyna ją od nowa). Jeśli fiszka jest w Fiszkach z powodu pewnego błędu, zostaje, bo tego
     * nie da się cofnąć.
     */
    @Transactional
    public FlashcardStatus remove(Long questionId, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        cardRepository.findById(questionId)
                .filter(card -> card.getCorrectOption() != null)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found"));
        boolean byMistake = reviewLogRepository.countConfidentMistakes(user.getId(), questionId) > 0;
        additionRepository.deleteByUserIdAndQuestionId(user.getId(), questionId);
        if (!byMistake) {
            List<Long> flashcardIds = cardRepository.findIdsByLinkedTestCardId(questionId);
            if (!flashcardIds.isEmpty()) {
                stateRepository.deleteByUserIdAndCardIds(user.getId(), flashcardIds);
            }
        }
        return statusOf(user.getId(), questionId, byMistake);
    }

    /**
     * Pytanie musi istnieć, być testowe, nieukryte i dostępne dla użytkownika (pula płaska albo przeczytany rozdział),
     * a do tego mieć widoczną fiszkę. Dla niedostępnego odpowiadamy tak samo jak dla nieistniejącego.
     */
    private void requireQuestionWithFlashcard(Long questionId, User user) {
        Card question = cardRepository.findById(questionId)
                .filter(card -> card.getCorrectOption() != null && !card.isHidden())
                .orElseThrow(() -> new ResourceNotFoundException("Question not found"));
        if (cardRepository.countReadableById(question.getId(), user.getId()) == 0) {
            throw new ResourceNotFoundException("Question not found");
        }
        if (!cardRepository.existsByLinkedTestCardIdAndHiddenFalse(questionId)) {
            throw new IllegalArgumentException("Do tego pytania nie ma fiszki.");
        }
    }
}
