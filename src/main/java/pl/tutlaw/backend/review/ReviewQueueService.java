package pl.tutlaw.backend.review;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import pl.tutlaw.backend.card.Card;
import pl.tutlaw.backend.card.CardRepository;
import pl.tutlaw.backend.review.dto.BacklogResponse;
import pl.tutlaw.backend.review.dto.ReviewQueueItemResponse;
import pl.tutlaw.backend.review.dto.TestQueueItemResponse;
import pl.tutlaw.backend.review.fsrs.FsrsEngine;
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
public class ReviewQueueService {

    private final CardRepository cardRepository;
    private final UserCardStateRepository stateRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final CurrentUserResolver currentUserResolver;
    private final FsrsEngine fsrsEngine;
    private final Clock clock;
    private final Random random;
    private final int newCardsPerDay;
    /** Osobny limit dla testów: długi test w stylu egzaminu nie powinien zużywać puli nowych fiszek. */
    private final int newTestQuestionsPerDay;
    /** Ile zaległych kart dziennie trafia do sesji (osobno dla fiszek i testów). Reszta czeka na kolejne dni. */
    private final int maxReviewsPerDay;

    @Autowired
    public ReviewQueueService(
            CardRepository cardRepository,
            UserCardStateRepository stateRepository,
            ReviewLogRepository reviewLogRepository,
            CurrentUserResolver currentUserResolver,
            FsrsEngine fsrsEngine,
            Clock clock,
            @Value("${app.fsrs.new-cards-per-day}") int newCardsPerDay,
            @Value("${app.fsrs.new-test-questions-per-day:40}") int newTestQuestionsPerDay,
            @Value("${app.fsrs.max-reviews-per-day:100}") int maxReviewsPerDay) {
        this(cardRepository, stateRepository, reviewLogRepository, currentUserResolver, fsrsEngine, clock,
                new Random(), newCardsPerDay, newTestQuestionsPerDay, maxReviewsPerDay);
    }

    /** Konstruktor z własnym generatorem losowym (testy mogą ustalić kolejność mieszania). */
    ReviewQueueService(
            CardRepository cardRepository,
            UserCardStateRepository stateRepository,
            ReviewLogRepository reviewLogRepository,
            CurrentUserResolver currentUserResolver,
            FsrsEngine fsrsEngine,
            Clock clock,
            Random random,
            int newCardsPerDay,
            int newTestQuestionsPerDay,
            int maxReviewsPerDay) {
        this.cardRepository = cardRepository;
        this.stateRepository = stateRepository;
        this.reviewLogRepository = reviewLogRepository;
        this.currentUserResolver = currentUserResolver;
        this.fsrsEngine = fsrsEngine;
        this.clock = clock;
        this.random = random;
        this.newCardsPerDay = newCardsPerDay;
        this.newTestQuestionsPerDay = newTestQuestionsPerDay;
        this.maxReviewsPerDay = maxReviewsPerDay;
    }

    /**
     * Kolejka fiszek. Gdy {@code categoryId}, {@code subjectId} i {@code sourceId} są puste, kolejka obejmuje
     * cały dostępny materiał użytkownika (globalne „Powtórki na dziś").
     * Zaległe od tych, które najpewniej już zapomniałeś (najniższa szansa przypomnienia), w liczbie do dziennego
     * limitu, potem nowe w kolejności rozdziałów. {@code extraDueReviews} dokłada kolejną porcję zaległych ponad limit.
     */
    public List<ReviewQueueItemResponse> getQueue(
            Long categoryId, Long subjectId, Long sourceId, Integer extraNewCards, Integer extraDueReviews,
            Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        List<Card> eligibleCards = getReadableCards(categoryId, subjectId, sourceId, user.getId());
        List<Card> queue = buildQueue(eligibleCards, user.getId(), extraNewCards, extraDueReviews, ReviewMode.FLASHCARD);
        return queue.stream().map(ReviewQueueMapper::toResponse).toList();
    }

    /**
     * Kolejka testu. Do sesji trafiają te same najpilniejsze zaległe pytania co w fiszkach (do dziennego limitu),
     * ale w sesji są losowo wymieszane (zaległe osobno, nowe osobno, zaległe na początku).
     */
    public List<TestQueueItemResponse> getTestQueue(
            Long categoryId, Long subjectId, Long sourceId, Integer extraNewCards, Integer extraDueReviews,
            Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        List<Card> eligibleCards = testCards(getReadableCards(categoryId, subjectId, sourceId, user.getId()));
        List<Card> queue = buildQueue(eligibleCards, user.getId(), extraNewCards, extraDueReviews, ReviewMode.TEST);
        return queue.stream().map(ReviewQueueMapper::toTestResponse).toList();
    }

    /**
     * Zaległości w wybranym zakresie: ile kart czeka i ile z nich nie zmieści się dziś w sesji (limit dzienny).
     * Ten sam zakres i ten sam tryb co kolejka, więc liczby zgadzają się z tym, co użytkownik dostaje.
     */
    public BacklogResponse getBacklog(
            Long categoryId, Long subjectId, Long sourceId, ReviewMode mode, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        List<Card> readable = getReadableCards(categoryId, subjectId, sourceId, user.getId());
        List<Card> eligible = mode == ReviewMode.TEST ? testCards(readable) : readable;

        LocalDateTime now = LocalDateTime.now(clock);
        long dueNow = splitByDue(eligible, loadScheduleByCardId(user.getId()), now).due().size();
        long doneToday = dueReviewsDoneToday(user.getId(), mode, now);
        int remaining = QueueRules.remainingAllowance(maxReviewsPerDay, doneToday, null);
        return new BacklogResponse(dueNow, QueueRules.deferred(dueNow, remaining), maxReviewsPerDay, doneToday);
    }

    public int countAvailableNewCards(Long categoryId, Long subjectId, Long sourceId, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        List<Card> readableCards = getReadableCards(categoryId, subjectId, sourceId, user.getId());
        Map<Long, UserCardStateRepository.CardDue> scheduleByCardId = loadScheduleByCardId(user.getId());

        return (int) readableCards.stream().filter(c -> !scheduleByCardId.containsKey(c.getId())).count();
    }

    /** Ile kart ma już minięty termin powtórki (bez nowych kart). */
    public long countDueNow(Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        return stateRepository.countDue(user.getId(), LocalDateTime.now(clock));
    }

    /** Pytania testowe (z poprawną odpowiedzią) spośród podanych kart. */
    private static List<Card> testCards(List<Card> cards) {
        return cards.stream().filter(c -> c.getCorrectOption() != null).toList();
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
    private Map<Long, UserCardStateRepository.CardDue> loadScheduleByCardId(Long userId) {
        Map<Long, UserCardStateRepository.CardDue> byCardId = new HashMap<>();
        for (UserCardStateRepository.CardDue row : stateRepository.findCardDueByUserId(userId)) {
            byCardId.put(row.getCardId(), row);
        }
        return byCardId;
    }

    /** Karty z minionym terminem (z szansą przypomnienia) i karty jeszcze nietknięte. */
    private record DueSplit(List<QueueRules.DueItem> due, List<Card> newCards) {
    }

    private DueSplit splitByDue(
            List<Card> eligibleCards, Map<Long, UserCardStateRepository.CardDue> scheduleByCardId, LocalDateTime now) {
        List<QueueRules.DueItem> due = new ArrayList<>();
        List<Card> newCards = new ArrayList<>();

        for (Card card : eligibleCards) {
            UserCardStateRepository.CardDue schedule = scheduleByCardId.get(card.getId());
            if (schedule == null) {
                newCards.add(card);
            } else if (!schedule.getDueDate().isAfter(now)) {
                double stability = schedule.getStability() == null ? 0 : schedule.getStability();
                double retrievability = fsrsEngine.retrievability(
                        QueueRules.elapsedDays(schedule.getLastReviewAt(), now), stability);
                due.add(new QueueRules.DueItem(card.getId(), schedule.getDueDate(), retrievability));
            }
        }
        return new DueSplit(due, newCards);
    }

    private long dueReviewsDoneToday(Long userId, ReviewMode mode, LocalDateTime now) {
        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        return reviewLogRepository.countDueReviewsBetween(userId, mode, startOfDay, startOfDay.plusDays(1));
    }

    private List<Card> buildQueue(
            List<Card> eligibleCards, Long userId, Integer extraNewCards, Integer extraDueReviews, ReviewMode mode) {
        LocalDateTime now = LocalDateTime.now(clock);
        DueSplit split = splitByDue(eligibleCards, loadScheduleByCardId(userId), now);

        Map<Long, Card> cardById = new HashMap<>();
        for (Card card : eligibleCards) {
            cardById.put(card.getId(), card);
        }

        // Najpierw najpilniejsze (najniższa szansa przypomnienia), ale tylko tyle, ile jeszcze mieści się w dziennym limicie.
        int allowance = QueueRules.remainingAllowance(
                maxReviewsPerDay, dueReviewsDoneToday(userId, mode, now), QueueRules.clampExtra(extraDueReviews));
        List<Card> dueCards = new ArrayList<>();
        for (QueueRules.DueItem item : QueueRules.prioritize(split.due())) {
            if (dueCards.size() >= allowance) break;
            dueCards.add(cardById.get(item.cardId()));
        }

        List<Card> candidateNewCards = new ArrayList<>(split.newCards());
        // Bez ORDER BY baza zwraca karty w nieokreślonej kolejności, więc „pierwsze 20 nowych" byłoby przypadkowe.
        candidateNewCards.sort(Comparator
                .comparingInt(ReviewQueueService::chapterOrder)
                .thenComparing(Card::getId));

        if (mode == ReviewMode.TEST) {
            Collections.shuffle(dueCards, random);
            Collections.shuffle(candidateNewCards, random);
        }

        int slots = newCardSlots(userId, now, QueueRules.clampExtra(extraNewCards), mode, candidateNewCards.size());

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
