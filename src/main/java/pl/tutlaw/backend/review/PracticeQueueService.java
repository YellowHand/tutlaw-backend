package pl.tutlaw.backend.review;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import pl.tutlaw.backend.card.Card;
import pl.tutlaw.backend.card.CardRepository;
import pl.tutlaw.backend.review.dto.TestQueueItemResponse;
import pl.tutlaw.backend.user.CurrentUserResolver;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sesja testowa z podanych pytań, np. "Przerób błędy teraz" po egzaminie próbnym. Odpowiedzi idą zwykłą drogą testu
 * (z oceną pamięci), więc to normalne ćwiczenie. Pytania niedostępne dla użytkownika, niezawieszone i w formacie
 * testowym zostają, reszta jest po cichu pomijana.
 */
@Service
@RequiredArgsConstructor
public class PracticeQueueService {

    static final int MAX_CARDS = 200;

    private final CardRepository cardRepository;
    private final CurrentUserResolver currentUserResolver;

    /** Pytania w kolejności z listy {@code cardIds} (bez powtórzeń). */
    public List<TestQueueItemResponse> queue(List<Long> cardIds, Authentication authentication) {
        if (cardIds == null || cardIds.isEmpty()) {
            throw new IllegalArgumentException("Nie podano pytań do przerobienia.");
        }
        Set<Long> ids = new LinkedHashSet<>(cardIds);
        if (ids.size() > MAX_CARDS) {
            throw new IllegalArgumentException("Za dużo pytań naraz (maksymalnie " + MAX_CARDS + ").");
        }

        Long userId = currentUserResolver.resolve(authentication).getId();
        Map<Long, Card> available = new HashMap<>();
        for (Card card : cardRepository.findPracticeTestCards(ids, userId)) {
            available.put(card.getId(), card);
        }
        return ids.stream()
                .map(available::get)
                .filter(card -> card != null)
                .map(ReviewQueueMapper::toTestResponse)
                .toList();
    }
}
