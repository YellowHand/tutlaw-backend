package pl.tutlaw.backend.review;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import pl.tutlaw.backend.card.CardRepository;
import pl.tutlaw.backend.card.CardScopes;
import pl.tutlaw.backend.card.QuestionOrigin;
import pl.tutlaw.backend.flashcard.FlashcardUnlockService;
import pl.tutlaw.backend.review.dto.BacklogResponse;
import pl.tutlaw.backend.review.dto.ReviewQueueItemResponse;
import pl.tutlaw.backend.review.dto.TestQueueItemResponse;
import pl.tutlaw.backend.review.fsrs.FsrsEngine;
import pl.tutlaw.backend.settings.EffectiveSettings;
import pl.tutlaw.backend.settings.LearningSettingsService;
import pl.tutlaw.backend.settings.OriginFilterService;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

@Service
public class ReviewQueueService {

    private final CardRepository cardRepository;
    private final UserCardStateRepository stateRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final CurrentUserResolver currentUserResolver;
    private final FsrsEngine fsrsEngine;
    private final Clock clock;
    private final Random random;
    /**
     * Dzienne limity użytkownika (nowe fiszki, nowe pytania testowe, zaległe powtórki) z jego ustawień nauki;
     * tam, gdzie niczego nie ustawił, obowiązują wartości domyślne z application.yml (app.fsrs.*).
     * Limit nowych pytań testowych jest osobny, żeby długi test w stylu egzaminu nie zużywał puli nowych fiszek,
     * a limit zaległych powtórek liczy się osobno dla fiszek i testów.
     */
    private final LearningSettingsService settingsService;
    private final LeechPolicy leechPolicy;
    /** Które pytania mają odblokowaną fiszkę (pewny błąd albo ręczne dodanie do fiszek). */
    private final FlashcardUnlockService unlockService;
    /** Filtr pochodzenia pytań w Testach (dotyczy tylko pytań testowych, nie fiszek). */
    private final OriginFilterService originFilter;

    @Autowired
    public ReviewQueueService(
            CardRepository cardRepository,
            UserCardStateRepository stateRepository,
            ReviewLogRepository reviewLogRepository,
            CurrentUserResolver currentUserResolver,
            FsrsEngine fsrsEngine,
            Clock clock,
            LearningSettingsService settingsService,
            LeechPolicy leechPolicy,
            FlashcardUnlockService unlockService,
            OriginFilterService originFilter) {
        this(cardRepository, stateRepository, reviewLogRepository, currentUserResolver, fsrsEngine, clock,
                new Random(), settingsService, leechPolicy, unlockService, originFilter);
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
            LearningSettingsService settingsService,
            LeechPolicy leechPolicy,
            FlashcardUnlockService unlockService,
            OriginFilterService originFilter) {
        this.cardRepository = cardRepository;
        this.stateRepository = stateRepository;
        this.reviewLogRepository = reviewLogRepository;
        this.currentUserResolver = currentUserResolver;
        this.fsrsEngine = fsrsEngine;
        this.clock = clock;
        this.random = random;
        this.settingsService = settingsService;
        this.leechPolicy = leechPolicy;
        this.unlockService = unlockService;
        this.originFilter = originFilter;
    }

    /**
     * Kolejka fiszek: tylko karty bez opcji A/B/C (pytania testowe żyją w Testach) i tylko odblokowane
     * (fiszka powiązana z pytaniem testowym pojawia się po pewnym błędzie w tym pytaniu albo po ręcznym dodaniu). Gdy {@code categoryId}, {@code subjectId} i {@code sourceId} są puste, kolejka obejmuje
     * cały dostępny materiał użytkownika (globalne „Powtórki na dziś").
     * Zaległe od tych, które najpewniej już zapomniałeś (najniższa szansa przypomnienia), w liczbie do dziennego
     * limitu, potem nowe w kolejności rozdziałów. {@code extraDueReviews} dokłada kolejną porcję zaległych ponad limit.
     */
    public List<ReviewQueueItemResponse> getQueue(
            Long categoryId, Long subjectId, Long sourceId, Integer extraNewCards, Integer extraDueReviews,
            Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        List<CardRepository.CardRef> eligibleCards = flashcards(
                getReadableCards(categoryId, subjectId, sourceId, user.getId()), user.getId());
        Map<Long, UserCardStateRepository.CardDue> scheduleByCardId = loadScheduleByCardId(user.getId());
        List<Long> queue = buildQueue(
                eligibleCards, scheduleByCardId, user.getId(), extraNewCards, extraDueReviews, ReviewMode.FLASHCARD);
        // Pełne karty ładujemy dopiero dla tych, które naprawdę trafiły do sesji (jedno zapytanie).
        return CardScopes.loadInOrder(cardRepository, queue).stream().map(card -> {
            int lapses = lapsesOf(scheduleByCardId.get(card.getId()));
            return ReviewQueueMapper.toResponse(card, lapses, leechPolicy.isLeech(lapses));
        }).toList();
    }

    /**
     * Kolejka testu. Do sesji trafiają te same najpilniejsze zaległe pytania co w fiszkach (do dziennego limitu),
     * ale w sesji są losowo wymieszane (zaległe osobno, nowe osobno, zaległe na początku).
     */
    public List<TestQueueItemResponse> getTestQueue(
            Long categoryId, Long subjectId, Long sourceId, Integer extraNewCards, Integer extraDueReviews,
            Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        List<CardRepository.CardRef> eligibleCards = testCards(
                getReadableCards(categoryId, subjectId, sourceId, user.getId()), originFilter.activeFor(user.getId()));
        List<Long> queue = buildQueue(
                eligibleCards, loadScheduleByCardId(user.getId()), user.getId(), extraNewCards, extraDueReviews,
                ReviewMode.TEST);
        return CardScopes.loadInOrder(cardRepository, queue).stream().map(ReviewQueueMapper::toTestResponse).toList();
    }

    /**
     * Zaległości w wybranym zakresie: ile kart czeka i ile z nich nie zmieści się dziś w sesji (limit dzienny).
     * Ten sam zakres i ten sam tryb co kolejka, więc liczby zgadzają się z tym, co użytkownik dostaje.
     */
    public BacklogResponse getBacklog(
            Long categoryId, Long subjectId, Long sourceId, ReviewMode mode, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        List<CardRepository.CardRef> readable = getReadableCards(categoryId, subjectId, sourceId, user.getId());
        List<CardRepository.CardRef> eligible = forMode(mode, readable, user.getId());

        LocalDateTime now = LocalDateTime.now(clock);
        long dueNow = splitByDue(eligible, loadScheduleByCardId(user.getId()), now).due().size();
        long doneToday = dueReviewsDoneToday(user.getId(), mode, now);
        int maxReviewsPerDay = settingsService.effectiveFor(user.getId()).maxReviewsPerDay();
        int remaining = QueueRules.remainingAllowance(maxReviewsPerDay, doneToday, null);
        return new BacklogResponse(dueNow, QueueRules.deferred(dueNow, remaining), maxReviewsPerDay, doneToday);
    }

    /** Ile nowych (jeszcze nietkniętych) kart danego trybu czeka w zakresie: fiszek albo pytań testowych. */
    public int countAvailableNewCards(
            Long categoryId, Long subjectId, Long sourceId, ReviewMode mode, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        List<CardRepository.CardRef> readableCards = forMode(
                mode, getReadableCards(categoryId, subjectId, sourceId, user.getId()), user.getId());
        Map<Long, UserCardStateRepository.CardDue> scheduleByCardId = loadScheduleByCardId(user.getId());

        return (int) readableCards.stream().filter(c -> !scheduleByCardId.containsKey(c.getId())).count();
    }

    /** Ile kart danego trybu (fiszek albo pytań testowych) ma już minięty termin powtórki (bez nowych kart). */
    public long countDueNow(ReviewMode mode, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        // Filtr pochodzenia dotyczy tylko pytań testowych; dla fiszek podajemy komplet (lista nie może być pusta).
        Set<QuestionOrigin> testOrigins = mode == ReviewMode.TEST
                ? originFilter.activeFor(user.getId()) : EnumSet.allOf(QuestionOrigin.class);
        return stateRepository.countDue(
                user.getId(), LocalDateTime.now(clock), mode == ReviewMode.FLASHCARD, mode == ReviewMode.TEST,
                testOrigins);
    }

    /**
     * Ile kart danego trybu będzie miało minięty termin powtórki do końca jutra: te, które czekają już teraz, i te,
     * które wrócą jutro. To liczba do podsumowania sesji ("Jutro wróci N powtórek"): dzisiejsze zaległości, które
     * nie zmieściły się w sesji, też czekają na jutro.
     */
    public long countDueByEndOfTomorrow(ReviewMode mode, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        Set<QuestionOrigin> testOrigins = mode == ReviewMode.TEST
                ? originFilter.activeFor(user.getId()) : EnumSet.allOf(QuestionOrigin.class);
        LocalDateTime endOfTomorrow = LocalDate.now(clock).plusDays(1).atTime(LocalTime.MAX);
        return stateRepository.countDue(
                user.getId(), endOfTomorrow, mode == ReviewMode.FLASHCARD, mode == ReviewMode.TEST, testOrigins);
    }

    /** Pytania testowe (z poprawną odpowiedzią) spośród podanych kart, z wybranych pochodzeń. */
    private static List<CardRepository.CardRef> testCards(
            List<CardRepository.CardRef> cards, Set<QuestionOrigin> origins) {
        return cards.stream()
                .filter(CardScopes::isTest)
                .filter(ref -> CardScopes.matchesOrigins(ref, origins))
                .toList();
    }

    /**
     * Fiszki spośród podanych kart: bez pytań testowych i bez fiszek powiązanych z pytaniem, którego użytkownik
     * jeszcze nie pomylił (pewnie) ani nie dodał do fiszek. Odblokowania sprawdzamy w historii odpowiedzi tylko wtedy, gdy jakaś fiszka jest powiązana.
     */
    private List<CardRepository.CardRef> flashcards(List<CardRepository.CardRef> cards, Long userId) {
        List<CardRepository.CardRef> plain = cards.stream().filter(CardScopes::isFlashcard).toList();
        if (!CardScopes.anyLinked(plain)) {
            return plain;
        }
        Set<Long> unlocked = unlockService.unlockedQuestionIds(userId);
        return plain.stream().filter(ref -> CardScopes.isUnlocked(ref, unlocked)).toList();
    }

    /** Karty trybu: pytania testowe dla Testów, odblokowane fiszki dla Fiszek. */
    private List<CardRepository.CardRef> forMode(ReviewMode mode, List<CardRepository.CardRef> cards, Long userId) {
        return mode == ReviewMode.TEST
                ? testCards(cards, originFilter.activeFor(userId)) : flashcards(cards, userId);
    }

    /** Zakres od najwęższego: źródło, przedmiot, kategoria (rodzaj), a bez żadnego cały materiał użytkownika. */
    private List<CardRepository.CardRef> getReadableCards(
            Long categoryId, Long subjectId, Long sourceId, Long userId) {
        return CardScopes.readableRefs(cardRepository, categoryId, subjectId, sourceId, userId);
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
    private record DueSplit(List<QueueRules.DueItem> due, List<CardRepository.CardRef> newCards) {
    }

    private DueSplit splitByDue(
            List<CardRepository.CardRef> eligibleCards,
            Map<Long, UserCardStateRepository.CardDue> scheduleByCardId, LocalDateTime now) {
        List<QueueRules.DueItem> due = new ArrayList<>();
        List<CardRepository.CardRef> newCards = new ArrayList<>();

        for (CardRepository.CardRef card : eligibleCards) {
            UserCardStateRepository.CardDue schedule = scheduleByCardId.get(card.getId());
            if (schedule == null) {
                newCards.add(card);
            } else if (!Boolean.TRUE.equals(schedule.getSuspended()) && !schedule.getDueDate().isAfter(now)) {
                // Zawieszona karta ma stan, więc nie jest „nowa", ale też nie wraca do kolejki.
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

    private static int lapsesOf(UserCardStateRepository.CardDue schedule) {
        return schedule == null || schedule.getLapses() == null ? 0 : schedule.getLapses();
    }

    /** Numery kart w kolejności sesji: najpierw zaległe do dziennego limitu, potem nowe. Same numery, bez treści. */
    private List<Long> buildQueue(
            List<CardRepository.CardRef> eligibleCards,
            Map<Long, UserCardStateRepository.CardDue> scheduleByCardId, Long userId,
            Integer extraNewCards, Integer extraDueReviews, ReviewMode mode) {
        LocalDateTime now = LocalDateTime.now(clock);
        EffectiveSettings settings = settingsService.effectiveFor(userId);
        DueSplit split = splitByDue(eligibleCards, scheduleByCardId, now);

        // Najpierw najpilniejsze (najniższa szansa przypomnienia), ale tylko tyle, ile jeszcze mieści się w dziennym limicie.
        int allowance = QueueRules.remainingAllowance(
                settings.maxReviewsPerDay(), dueReviewsDoneToday(userId, mode, now), QueueRules.clampExtra(extraDueReviews));
        List<Long> dueIds = new ArrayList<>();
        for (QueueRules.DueItem item : QueueRules.prioritize(split.due())) {
            if (dueIds.size() >= allowance) break;
            dueIds.add(item.cardId());
        }

        List<CardRepository.CardRef> candidateNewCards = new ArrayList<>(split.newCards());
        // Bez ORDER BY baza zwraca karty w nieokreślonej kolejności, więc „pierwsze 20 nowych" byłoby przypadkowe.
        candidateNewCards.sort(Comparator
                .comparingInt(ReviewQueueService::chapterOrder)
                .thenComparing(CardRepository.CardRef::getId));

        if (mode == ReviewMode.TEST) {
            Collections.shuffle(dueIds, random);
            Collections.shuffle(candidateNewCards, random);
        }

        int slots = newCardSlots(
                userId, now, QueueRules.clampExtra(extraNewCards), mode, candidateNewCards.size(), settings);

        List<Long> queue = new ArrayList<>(dueIds);
        candidateNewCards.stream().limit(slots).map(CardRepository.CardRef::getId).forEach(queue::add);
        return queue;
    }

    private int newCardSlots(
            Long userId, LocalDateTime now, Integer extraNewCards, ReviewMode mode, int available,
            EffectiveSettings settings) {
        if (extraNewCards != null) {
            return Math.min(extraNewCards, available);
        }
        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        long introducedToday = reviewLogRepository.countFirstReviewsBetween(
                userId, mode, startOfDay, startOfDay.plusDays(1));
        // Pytania testowe mają limit własny albo automatyczny (idzie za planem do egzaminu); fiszki zawsze stały.
        int dailyLimit = mode == ReviewMode.TEST
                ? settingsService.dailyNewTestQuestionLimit(userId)
                : settings.newCardsPerDay();
        return (int) Math.max(0, dailyLimit - introducedToday);
    }

    private static int chapterOrder(CardRepository.CardRef card) {
        return card.getChapterOrder() == null ? 0 : card.getChapterOrder();
    }
}
