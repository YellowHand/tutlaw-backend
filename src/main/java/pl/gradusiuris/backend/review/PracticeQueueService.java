package pl.gradusiuris.backend.review;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import pl.gradusiuris.backend.pool.PoolService;
import pl.gradusiuris.backend.question.Question;
import pl.gradusiuris.backend.question.QuestionRepository;
import pl.gradusiuris.backend.review.dto.TestQueueItemResponse;
import pl.gradusiuris.backend.user.CurrentUserResolver;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sesja testowa z podanych pytań, np. "Przerób błędy teraz" po egzaminie próbnym. Odpowiedzi idą zwykłą drogą testu
 * (z oceną pamięci), więc to normalne ćwiczenie. Pytania spoza aktów użytkownika, ukryte i zawieszone w testach są po
 * cichu pomijane, reszta zostaje.
 */
@Service
@RequiredArgsConstructor
public class PracticeQueueService {

    static final int MAX_QUESTIONS = 200;

    private final QuestionRepository questionRepository;
    private final CurrentUserResolver currentUserResolver;
    private final PoolService poolService;

    /** Pytania w kolejności z listy {@code questionIds} (bez powtórzeń). */
    public List<TestQueueItemResponse> queue(List<Long> questionIds, Authentication authentication) {
        if (questionIds == null || questionIds.isEmpty()) {
            throw new IllegalArgumentException("Nie podano pytań do przerobienia.");
        }
        Set<Long> ids = new LinkedHashSet<>(questionIds);
        if (ids.size() > MAX_QUESTIONS) {
            throw new IllegalArgumentException("Za dużo pytań naraz (maksymalnie " + MAX_QUESTIONS + ").");
        }

        Long userId = currentUserResolver.resolve(authentication).getId();
        // Akty bez zawężenia użytkownika: sesja składa się z pytań wybranych wcześniej, a zawężenie ustawione
        // w międzyczasie nie powinno jej podmieniać.
        List<Long> actIds = poolService.forUser(userId).unnarrowed().actIds();
        Map<Long, Question> available = new HashMap<>();
        for (Question question : questionRepository.findPracticeQuestions(ids, userId, actIds)) {
            available.put(question.getId(), question);
        }
        return ids.stream()
                .map(available::get)
                .filter(question -> question != null)
                .map(ReviewQueueMapper::toTest)
                .toList();
    }
}
