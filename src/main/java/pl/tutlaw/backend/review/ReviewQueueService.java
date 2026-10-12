package pl.tutlaw.backend.review;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import pl.tutlaw.backend.card.Card;
import pl.tutlaw.backend.card.CardRepository;
import pl.tutlaw.backend.review.dto.ReviewQueueItemResponse;
import pl.tutlaw.backend.review.dto.TestQueueItemResponse;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewQueueService {

    private final CardRepository cardRepository;
    private final UserCardStateRepository stateRepository;
    private final CurrentUserResolver currentUserResolver;

    @Value("${app.fsrs.new-cards-per-day}")
    private int newCardsPerDay;

    public List<ReviewQueueItemResponse> getQueue(
            Long subjectId, Long sourceId, Integer extraNewCards, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        List<Card> eligibleCards = getReadableCards(subjectId, sourceId, user.getId());
        List<Card> dueCards = buildDueQueue(eligibleCards, user.getId(), extraNewCards);
        return dueCards.stream().map(ReviewQueueMapper::toResponse).toList();
    }

    public List<TestQueueItemResponse> getTestQueue(
            Long subjectId, Long sourceId, Integer extraNewCards, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        List<Card> eligibleCards = getReadableCards(subjectId, sourceId, user.getId()).stream()
                .filter(c -> c.getCorrectOption() != null)
                .toList();
        List<Card> dueCards = buildDueQueue(eligibleCards, user.getId(), extraNewCards);
        return dueCards.stream().map(ReviewQueueMapper::toTestResponse).toList();
    }

    public int countAvailableNewCards(Long subjectId, Long sourceId, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        List<Card> readableCards = getReadableCards(subjectId, sourceId, user.getId());

        List<Long> cardIds = readableCards.stream().map(Card::getId).toList();
        Set<Long> cardsWithState = stateRepository.findByUserIdAndCardIdIn(user.getId(), cardIds).stream()
                .map(s -> s.getCard().getId())
                .collect(Collectors.toSet());

        return (int) readableCards.stream().filter(c -> !cardsWithState.contains(c.getId())).count();
    }

    private List<Card> getReadableCards(Long subjectId, Long sourceId, Long userId) {
        return sourceId != null
                ? cardRepository.findReadableCardsForUserAndSource(sourceId, userId)
                : cardRepository.findReadableCardsForUserAndSubject(subjectId, userId);
    }

    private List<Card> buildDueQueue(List<Card> eligibleCards, Long userId, Integer extraNewCards) {
        LocalDateTime now = LocalDateTime.now();

        List<Card> queue = new ArrayList<>();
        List<Card> candidateNewCards = new ArrayList<>();

        for (Card card : eligibleCards) {
            Optional<UserCardState> stateOpt = stateRepository.findByUserIdAndCardId(userId, card.getId());
            if (stateOpt.isPresent()) {
                if (!stateOpt.get().getDueDate().isAfter(now)) {
                    queue.add(card);
                }
            } else {
                candidateNewCards.add(card);
            }
        }

        int slots;
        if (extraNewCards != null) {
            slots = Math.min(extraNewCards, candidateNewCards.size());
        } else {
            LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
            LocalDateTime endOfDay = startOfDay.plusDays(1);
            int introducedToday = stateRepository.countNewCardsIntroducedToday(userId, startOfDay, endOfDay);
            slots = Math.max(0, newCardsPerDay - introducedToday);
        }

        candidateNewCards.stream().limit(slots).forEach(queue::add);
        return queue;
    }
}