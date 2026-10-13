package pl.tutlaw.backend.flashcard;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.tutlaw.backend.review.ReviewLogRepository;

import java.util.HashSet;
import java.util.Set;

/**
 * Które pytania mają odblokowaną fiszkę w Fiszkach tego użytkownika. Fiszka każdego pytania powstaje automatycznie,
 * ale użytkownik widzi ją dopiero, gdy pomylił to pytanie bez zaznaczenia „Nie jestem pewien" (pewny błąd)
 * albo sam dodał ją przyciskiem „Dodaj do fiszek".
 */
@Service
@RequiredArgsConstructor
public class FlashcardUnlockService {

    private final ReviewLogRepository reviewLogRepository;
    private final FlashcardAdditionRepository additionRepository;

    /** Numery pytań testowych, których fiszka jest dla użytkownika odblokowana (pewny błąd albo ręczne dodanie). */
    public Set<Long> unlockedQuestionIds(Long userId) {
        Set<Long> unlocked = new HashSet<>(reviewLogRepository.findConfidentMistakeCardIds(userId));
        unlocked.addAll(additionRepository.findQuestionIdsByUserId(userId));
        return unlocked;
    }
}
