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

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class ReviewQueueService {

    private final CardRepository cardRepository;
    private final UserCardStateRepository stateRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final CurrentUserResolver currentUserResolver;
    private final Clock clock;

    private final Random random = new Random();

    @Value("${app.fsrs.new-cards-per-day}")
    private int newCardsPerDay;

    /** Osobny limit dla testów: długi test w stylu egzaminu nie powinien zużywać puli nowych fiszek. */
    @Value("${app.fsrs.new-test-questions-per-day:40}")
    private int newTestQuestionsPerDay;

    /**
     * Kolejka fiszek. Gdy {@code categoryId}, {@code subjectId} i {@code sourceId} są puste, kolejka obejmuje
     * cały dostępny materiał użytkownika (globalne „Powtórki na dziś").
     * Zaległe od najstarszej, potem nowe w kolejności rozdziałów.
     */
    public List<ReviewQueueItemResponse> getQueue(
            Long categoryId, Long subjectId, Long sourceId, Integer extraNewCards, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        List<Card> eligibleCards = getReadableCards(categoryId, subjectId, sourceId, user.getId());
        List<Card> dueCards = buildDueQueue(eligibleCards, user.getId(), extraNewCards, ReviewMode.FLASHCARD);
        return dueCards.stream().map(ReviewQueueMapper::toResponse).toList();
    }

    /** Kolejka testu. Pytania są losowo wymieszane (zaległe osobno, nowe osobno, zaległe na początku). */
    public List<TestQueueItemResponse> getTestQueue(
            Long categoryId, Long subjectId, Long sourceId, Integer extraNewCards, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        List<Card> eligibleCards = getReadableCards(categoryId, subjectId, sourceId, user.getId()).stream()
                .filter(c -> c.getCorrectOption() != null)
                .toList();
        List<Card> dueCards = buildDueQueue(eligibleCards, user.getId(), extraNewCards, ReviewMode.TEST);
        return dueCards.stream().map(ReviewQueueMapper::toTestResponse).toList();
    }

    public int countAvailableNewCards(Long categoryId, Long subjectId, Long sourceId, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        List<Card> readableCards = getReadableCards(categoryId, subjectId, sourceId, user.getId());
        Map<Long, LocalDateTime> dueByCardId = loadDueByCardId(user.getId());

        return (int) readableCards.stream().filter(c -> !dueByCardId.containsKey(c.getId())).count();
    }

    /** Ile kart ma już minięty termin powtórki (bez nowych kart). */
    public long countDueNow(Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        return stateRepository.countDue(user.getId(), LocalDateTime.now(clock));
    }

    /** Zakres od najwęższego: źródło, przedmiot, kategoria (rodzaj), a bez żadnego cały materiał użytkownika. */
    private List<Card> getReadableCards(Long categoryId, Long subjectId, Long sourceId, Long userId) {
        if (sourceId != null) {
            return cardRepository.findReadableCardsForUserAndSource(sourceId, userId);
        }
        if (subjectId != null) {
            return cardRepository.findReadableCardsForUserAndSubject(subjectId, userId);
        }
        if (categoryId != null) {
            return cardRepository.findReadableCardsForUserAndCategory(categoryId, userId);
        }
        return cardRepository.findReadableCardsForUser(userId);
    }

    /** Jedno zapytanie na użytkownika zamiast jednego na każdą kartę osobno. */
    private Map<Long, LocalDateTime> loadDueByCardId(Long userId) {
        Map<Long, LocalDateTime> dueByCardId = new HashMap<>();
        for (UserCardStateRepository.CardDue row : stateRepository.findCardDueByUserId(userId)) {
            dueByCardId.put(row.getCardId(), row.getDueDate());
        }
        return dueByCardId;
    }

    private List<Card> buildDueQueue(List<Card> eligibleCards, Long userId, Integer extraNewCards, ReviewMode mode) {
        LocalDateTime now = LocalDateTime.now(clock);
        Map<Long, LocalDateTime> dueByCardId = loadDueByCardId(userId);

        List<Card> dueCards = new ArrayList<>();
        List<Card> candidateNewCards = new ArrayList<>();

        for (Card card : eligibleCards) {
            LocalDateTime dueDate = dueByCardId.get(card.getId());
            if (dueDate != null) {
                if (!dueDate.isAfter(now)) {
                    dueCards.add(card);
                }
            } else {
                candidateNewCards.add(card);
            }
        }

        // Bez ORDER BY baza zwraca karty w nieokreślonej kolejności, więc „pierwsze 20 nowych" byłoby przypadkowe.
        dueCards.sort(Comparator
                .comparing((Card c) -> dueByCardId.get(c.getId()))
                .thenComparing(Card::getId));
        candidateNewCards.sort(Comparator
                .comparingInt(ReviewQueueService::chapterOrder)
                .thenComparing(Card::getId));

        if (mode == ReviewMode.TEST) {
            Collections.shuffle(dueCards, random);
            Collections.shuffle(candidateNewCards, random);
        }

        int slots = newCardSlots(userId, now, extraNewCards, mode, candidateNewCards.size());

        List<Card> queue = new ArrayList<>(dueCards);
        candidateNewCards.stream().limit(slots).forEach(queue::add);
        return queue;
    }

    private int newCardSlots(Long userId, LocalDateTime now, Integer extraNewCards, ReviewMode mode, int available) {
        if (extraNewCards != null) {
            return Math.min(extraNewCards, available);
        }
        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        long introducedToday = reviewLogRepository.countFirstReviewsBetween(
                userId, mode, startOfDay, startOfDay.plusDays(1));
        int dailyLimit = mode == ReviewMode.TEST ? newTestQuestionsPerDay : newCardsPerDay;
        return (int) Math.max(0, dailyLimit - introducedToday);
    }

    private static int chapterOrder(Card card) {
        return card.getChapter() == null ? 0 : card.getChapter().getOrderIndex();
    }
}
