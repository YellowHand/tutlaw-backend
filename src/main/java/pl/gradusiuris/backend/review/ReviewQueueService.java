package pl.gradusiuris.backend.review;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import pl.gradusiuris.backend.flashcard.FlashcardUnlockService;
import pl.gradusiuris.backend.pool.Pool;
import pl.gradusiuris.backend.pool.PoolService;
import pl.gradusiuris.backend.question.QuestionRef;
import pl.gradusiuris.backend.question.QuestionRepository;
import pl.gradusiuris.backend.question.QuestionScopes;
import pl.gradusiuris.backend.review.dto.BacklogResponse;
import pl.gradusiuris.backend.review.dto.FlashcardQueueItemResponse;
import pl.gradusiuris.backend.review.dto.TestQueueItemResponse;
import pl.gradusiuris.backend.review.fsrs.FsrsEngine;
import pl.gradusiuris.backend.settings.EffectiveSettings;
import pl.gradusiuris.backend.settings.LearningSettingsService;
import pl.gradusiuris.backend.user.CurrentUserResolver;
import pl.gradusiuris.backend.user.User;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Kolejki nauki w obu trybach tych samych pytań: Testy (pytania ABC) i Fiszki (to samo pytanie w trybie fiszki).
 * Każdy tryb ma własny stan FSRS, własne dzienne limity i własną listę zaległych. Zakres kolejki od najwęższego:
 * źródło (arkusz), akt prawny, dziedzina; bez żadnego cała pula użytkownika.
 */
@Service
public class ReviewQueueService {

    private final QuestionRepository questionRepository;
    private final UserQuestionStateRepository stateRepository;
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
    /** Pula pytań użytkownika (akty ścieżki, zawężenie, wszystkie źródła). */
    private final PoolService poolService;

    @Autowired
    public ReviewQueueService(
            QuestionRepository questionRepository,
            UserQuestionStateRepository stateRepository,
            ReviewLogRepository reviewLogRepository,
            CurrentUserResolver currentUserResolver,
            FsrsEngine fsrsEngine,
            Clock clock,
            LearningSettingsService settingsService,
            LeechPolicy leechPolicy,
            FlashcardUnlockService unlockService,
            PoolService poolService) {
        this(questionRepository, stateRepository, reviewLogRepository, currentUserResolver, fsrsEngine, clock,
                new Random(), settingsService, leechPolicy, unlockService, poolService);
    }

    /** Konstruktor z własnym generatorem losowym (testy mogą ustalić kolejność mieszania). */
    ReviewQueueService(
            QuestionRepository questionRepository,
            UserQuestionStateRepository stateRepository,
            ReviewLogRepository reviewLogRepository,
            CurrentUserResolver currentUserResolver,
            FsrsEngine fsrsEngine,
            Clock clock,
            Random random,
            LearningSettingsService settingsService,
            LeechPolicy leechPolicy,
            FlashcardUnlockService unlockService,
            PoolService poolService) {
        this.questionRepository = questionRepository;
        this.stateRepository = stateRepository;
        this.reviewLogRepository = reviewLogRepository;
        this.currentUserResolver = currentUserResolver;
        this.fsrsEngine = fsrsEngine;
        this.clock = clock;
        this.random = random;
        this.settingsService = settingsService;
        this.leechPolicy = leechPolicy;
        this.unlockService = unlockService;
        this.poolService = poolService;
    }

    /**
     * Kolejka fiszek: tylko odblokowane fiszki (fiszka pytania pojawia się po pewnym błędzie w tym pytaniu albo po
     * ręcznym dodaniu). Gdy {@code fieldId}, {@code actId} i {@code sourceId} są puste, kolejka obejmuje całą pulę
     * użytkownika (globalne „Powtórki na dziś"). Zaległe od tych, które najpewniej już zapomniałeś (najniższa szansa
     * przypomnienia), w liczbie do dziennego limitu, potem nowe. {@code extraDueReviews} dokłada kolejną porcję
     * zaległych ponad limit.
     */
    public List<FlashcardQueueItemResponse> getFlashcardQueue(
            Long fieldId, Long actId, Long sourceId, Integer extraNewCards, Integer extraDueReviews,
            Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        List<QuestionRef> eligible = eligibleFor(ReviewMode.FLASHCARD, fieldId, actId, sourceId, user.getId());
        Map<Long, UserQuestionStateRepository.QuestionDue> schedule = loadSchedule(user.getId(), ReviewMode.FLASHCARD);
        List<Long> queue = buildQueue(
                eligible, schedule, user.getId(), extraNewCards, extraDueReviews, ReviewMode.FLASHCARD);
        // Pełne pytania ładujemy dopiero dla tych, które naprawdę trafiły do sesji (jedno zapytanie).
        return QuestionScopes.loadInOrder(questionRepository, queue).stream().map(question -> {
            int lapses = lapsesOf(schedule.get(question.getId()));
            return ReviewQueueMapper.toFlashcard(question, lapses, leechPolicy.isLeech(lapses));
        }).toList();
    }

    /**
     * Kolejka testu. Do sesji trafiają te same najpilniejsze zaległe pytania co w fiszkach (do dziennego limitu),
     * ale w sesji są losowo wymieszane (zaległe osobno, nowe osobno, zaległe na początku).
     */
    public List<TestQueueItemResponse> getTestQueue(
            Long fieldId, Long actId, Long sourceId, Integer extraNewCards, Integer extraDueReviews,
            Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        List<QuestionRef> eligible = eligibleFor(ReviewMode.TEST, fieldId, actId, sourceId, user.getId());
        List<Long> queue = buildQueue(
                eligible, loadSchedule(user.getId(), ReviewMode.TEST), user.getId(), extraNewCards, extraDueReviews,
                ReviewMode.TEST);
        return QuestionScopes.loadInOrder(questionRepository, queue).stream().map(ReviewQueueMapper::toTest).toList();
    }

    /**
     * Zaległości w wybranym zakresie: ile pytań czeka i ile z nich nie zmieści się dziś w sesji (limit dzienny).
     * Ten sam zakres i ten sam tryb co kolejka, więc liczby zgadzają się z tym, co użytkownik dostaje.
     */
    public BacklogResponse getBacklog(
            Long fieldId, Long actId, Long sourceId, ReviewMode mode, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        List<QuestionRef> eligible = eligibleFor(mode, fieldId, actId, sourceId, user.getId());

        LocalDateTime now = LocalDateTime.now(clock);
        long dueNow = splitByDue(eligible, loadSchedule(user.getId(), mode), now).due().size();
        long doneToday = dueReviewsDoneToday(user.getId(), mode, now);
        int maxReviewsPerDay = settingsService.effectiveFor(user.getId()).maxReviewsPerDay();
        int remaining = QueueRules.remainingAllowance(maxReviewsPerDay, doneToday, null);
        return new BacklogResponse(dueNow, QueueRules.deferred(dueNow, remaining), maxReviewsPerDay, doneToday);
    }

    /** Ile nowych (jeszcze nietkniętych w tym trybie) pytań czeka w zakresie: fiszek albo pytań testowych. */
    public int countAvailableNewQuestions(
            Long fieldId, Long actId, Long sourceId, ReviewMode mode, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        List<QuestionRef> eligible = eligibleFor(mode, fieldId, actId, sourceId, user.getId());
        Map<Long, UserQuestionStateRepository.QuestionDue> schedule = loadSchedule(user.getId(), mode);

        return (int) eligible.stream().filter(q -> !schedule.containsKey(q.getId())).count();
    }

    /** Ile pytań danego trybu (fiszek albo testów) ma już minięty termin powtórki (bez nowych). */
    public long countDueNow(ReviewMode mode, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        Pool pool = poolFor(mode, user.getId());
        return stateRepository.countDue(
                user.getId(), mode, LocalDateTime.now(clock), pool.actIds(), pool.sourceIds());
    }

    /**
     * Ile pytań danego trybu będzie miało minięty termin powtórki do końca jutra: te, które czekają już teraz, i te,
     * które wrócą jutro. To liczba do podsumowania sesji ("Jutro wróci N powtórek"): dzisiejsze zaległości, które
     * nie zmieściły się w sesji, też czekają na jutro.
     */
    public long countDueByEndOfTomorrow(ReviewMode mode, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        Pool pool = poolFor(mode, user.getId());
        LocalDateTime endOfTomorrow = LocalDate.now(clock).plusDays(1).atTime(LocalTime.MAX);
        return stateRepository.countDue(user.getId(), mode, endOfTomorrow, pool.actIds(), pool.sourceIds());
    }

    /** Pula trybu (akty ścieżki z zawężeniem, wszystkie źródła). */
    private Pool poolFor(ReviewMode mode, Long userId) {
        Pool pool = poolService.forUser(userId);
        return mode == ReviewMode.FLASHCARD ? pool.forFlashcards() : pool;
    }

    /**
     * Pytania trybu w zakresie: dla Testów wszystkie z puli, dla Fiszek tylko te z odblokowaną fiszką
     * (pewny błąd albo ręczne dodanie) i widoczną fiszką.
     */
    private List<QuestionRef> eligibleFor(ReviewMode mode, Long fieldId, Long actId, Long sourceId, Long userId) {
        Pool pool = poolFor(mode, userId);
        List<QuestionRef> refs = QuestionScopes.inScope(
                questionRepository.findRefsInPool(pool.actIds(), pool.sourceIds()), fieldId, actId, sourceId);
        if (mode == ReviewMode.TEST) {
            return refs;
        }
        Set<Long> unlocked = unlockService.unlockedQuestionIds(userId);
        return refs.stream().filter(ref -> QuestionScopes.isUnlocked(ref, unlocked)).toList();
    }

    /** Jedno zapytanie na użytkownika i tryb zamiast jednego na każde pytanie osobno. */
    private Map<Long, UserQuestionStateRepository.QuestionDue> loadSchedule(Long userId, ReviewMode mode) {
        Map<Long, UserQuestionStateRepository.QuestionDue> byQuestionId = new HashMap<>();
        for (UserQuestionStateRepository.QuestionDue row : stateRepository.findScheduleByUserIdAndMode(userId, mode)) {
            byQuestionId.put(row.getQuestionId(), row);
        }
        return byQuestionId;
    }

    /** Pytania z minionym terminem (z szansą przypomnienia) i pytania jeszcze nietknięte w tym trybie. */
    private record DueSplit(List<QueueRules.DueItem> due, List<QuestionRef> newQuestions) {
    }

    private DueSplit splitByDue(
            List<QuestionRef> eligible,
            Map<Long, UserQuestionStateRepository.QuestionDue> schedule, LocalDateTime now) {
        List<QueueRules.DueItem> due = new ArrayList<>();
        List<QuestionRef> newQuestions = new ArrayList<>();

        for (QuestionRef question : eligible) {
            UserQuestionStateRepository.QuestionDue state = schedule.get(question.getId());
            if (state == null) {
                newQuestions.add(question);
            } else if (!Boolean.TRUE.equals(state.getSuspended()) && !state.getDueDate().isAfter(now)) {
                // Zawieszone pytanie ma stan, więc nie jest „nowe", ale też nie wraca do kolejki.
                double stability = state.getStability() == null ? 0 : state.getStability();
                double retrievability = fsrsEngine.retrievability(
                        QueueRules.elapsedDays(state.getLastReviewAt(), now), stability);
                due.add(new QueueRules.DueItem(question.getId(), state.getDueDate(), retrievability));
            }
        }
        return new DueSplit(due, newQuestions);
    }

    private long dueReviewsDoneToday(Long userId, ReviewMode mode, LocalDateTime now) {
        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        return reviewLogRepository.countDueReviewsBetween(userId, mode, startOfDay, startOfDay.plusDays(1));
    }

    private static int lapsesOf(UserQuestionStateRepository.QuestionDue state) {
        return state == null || state.getLapses() == null ? 0 : state.getLapses();
    }

    /** Numery pytań w kolejności sesji: najpierw zaległe do dziennego limitu, potem nowe. Same numery, bez treści. */
    private List<Long> buildQueue(
            List<QuestionRef> eligible,
            Map<Long, UserQuestionStateRepository.QuestionDue> schedule, Long userId,
            Integer extraNewCards, Integer extraDueReviews, ReviewMode mode) {
        LocalDateTime now = LocalDateTime.now(clock);
        EffectiveSettings settings = settingsService.effectiveFor(userId);
        DueSplit split = splitByDue(eligible, schedule, now);

        // Najpierw najpilniejsze (najniższa szansa przypomnienia), ale tylko tyle, ile jeszcze mieści się w dziennym limicie.
        int allowance = QueueRules.remainingAllowance(
                settings.maxReviewsPerDay(), dueReviewsDoneToday(userId, mode, now), QueueRules.clampExtra(extraDueReviews));
        List<Long> dueIds = new ArrayList<>();
        for (QueueRules.DueItem item : QueueRules.prioritize(split.due())) {
            if (dueIds.size() >= allowance) break;
            dueIds.add(item.questionId());
        }

        List<QuestionRef> candidateNew = new ArrayList<>(split.newQuestions());
        // Bez ORDER BY baza zwraca pytania w nieokreślonej kolejności, więc „pierwsze 20 nowych" byłoby przypadkowe.
        candidateNew.sort(Comparator.comparing(QuestionRef::getId));

        if (mode == ReviewMode.TEST) {
            Collections.shuffle(dueIds, random);
            Collections.shuffle(candidateNew, random);
        }

        int slots = newQuestionSlots(
                userId, now, QueueRules.clampExtra(extraNewCards), mode, candidateNew.size(), settings);

        List<Long> queue = new ArrayList<>(dueIds);
        candidateNew.stream().limit(slots).map(QuestionRef::getId).forEach(queue::add);
        return queue;
    }

    private int newQuestionSlots(
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
                : settings.newFlashcardsPerDay();
        return (int) Math.max(0, dailyLimit - introducedToday);
    }
}
