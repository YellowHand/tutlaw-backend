package pl.tutlaw.backend.statistics;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tutlaw.backend.common.GroupCount;
import pl.tutlaw.backend.flashcard.FlashcardUnlockService;
import pl.tutlaw.backend.legal.LegalAct;
import pl.tutlaw.backend.legal.LegalActRepository;
import pl.tutlaw.backend.pool.Pool;
import pl.tutlaw.backend.pool.PoolService;
import pl.tutlaw.backend.question.Question;
import pl.tutlaw.backend.question.QuestionRef;
import pl.tutlaw.backend.question.QuestionRepository;
import pl.tutlaw.backend.question.QuestionScopes;
import pl.tutlaw.backend.review.ReviewLogRepository;
import pl.tutlaw.backend.review.ReviewMode;
import pl.tutlaw.backend.review.UserQuestionStateRepository;
import pl.tutlaw.backend.review.UserQuestionStateRepository.DueRow;
import pl.tutlaw.backend.review.UserQuestionStateRepository.StartedSummary;
import pl.tutlaw.backend.review.fsrs.FsrsEngine;
import pl.tutlaw.backend.statistics.dto.*;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Statystyki nauki. Dla obu trybów (Testy i Fiszki) liczone na tych samych pytaniach, ale na osobnych stanach FSRS:
 * parametr {@code mode} wybiera tryb, a brak parametru oznacza oba razem. Zakres to pula użytkownika (akty jego
 * ścieżki i zawężenie do aktów; źródło wybiera się osobno przy teście, więc statystyki liczą wszystkie).
 */
@Service
@RequiredArgsConstructor
public class StatisticsService {

    private static final double MATURE_STABILITY_THRESHOLD = 21.0;
    /** Dowolna stabilność: do zliczania wszystkich rozpoczętych pytań. */
    private static final double ANY_STABILITY = -1.0;
    private static final int RETENTION_WINDOW_DAYS = 30;
    /** Wskaźnik zapamiętywania liczymy z powtórek po co najmniej tylu dniach od poprzedniej odpowiedzi. */
    private static final double RETENTION_MIN_ELAPSED_DAYS = 1.0;
    private static final int MAX_ACTIVITY_DAYS = 365;
    private static final int MAX_WEAK_QUESTIONS = 100;
    private static final int TEST_ACCURACY_WINDOW_DAYS = 30;
    private static final int TEST_ACTIVITY_DAYS = 28;

    private final QuestionRepository questionRepository;
    private final LegalActRepository actRepository;
    private final UserQuestionStateRepository stateRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final CurrentUserResolver currentUserResolver;
    private final FsrsEngine fsrsEngine;
    private final Clock clock;
    private final PoolService poolService;
    /** Które pytania mają odblokowaną fiszkę (pewny błąd albo ręczne dodanie do fiszek). */
    private final FlashcardUnlockService unlockService;

    /**
     * Przegląd nauki. „Wszystkie pytania" to pytania, które użytkownik może teraz ćwiczyć w trybie (w fiszkach tylko
     * odblokowane). {@code actId} zawęża do jednego aktu prawnego; {@code mode} null to oba tryby razem.
     */
    @Transactional(readOnly = true)
    public OverviewResponse getOverview(Long actId, ReviewMode mode, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        Long userId = user.getId();
        Pool pool = poolService.forUser(userId);

        int total = 0;
        int started = 0;
        int mature = 0;
        List<DueRow> due = new ArrayList<>();
        LocalDate today = LocalDate.now(clock);
        // Bez dolnej granicy: pytania z minionym terminem (także z wcześniejszych dni) liczą się do „dziś".
        LocalDateTime end = today.plusDays(7).atStartOfDay();

        for (ReviewMode m : modes(mode)) {
            Pool modePool = poolFor(pool, m);
            total += (int) countAvailable(userId, modePool, m, actId);
            started += (int) sumFor(stateRepository.countStartedByAct(
                    userId, m, ANY_STABILITY, modePool.actIds(), modePool.sourceIds()), actId);
            mature += (int) sumFor(stateRepository.countStartedByAct(
                    userId, m, MATURE_STABILITY_THRESHOLD, modePool.actIds(), modePool.sourceIds()), actId);
            for (DueRow row : stateRepository.findDueUntil(userId, m, end, modePool.actIds(), modePool.sourceIds())) {
                if (actId == null || actId.equals(row.getLegalActId())) {
                    due.add(row);
                }
            }
        }

        Double retentionRate = calculateRetentionRate(userId, actId, mode);
        return new OverviewResponse(total, started, mature, retentionRate, forecast(due, today));
    }

    /**
     * Opanowanie aktów prawnych z puli użytkownika: ile pytań, ile zaczętych i ile utrwalonych w każdym akcie.
     * {@code mode} null to oba tryby razem.
     */
    @Transactional(readOnly = true)
    public List<ActMasteryResponse> getMasteryByAct(ReviewMode mode, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        Long userId = user.getId();
        Pool pool = poolService.forUser(userId);

        Map<Long, Long> totalByAct = new HashMap<>();
        Map<Long, Long> startedByAct = new HashMap<>();
        Map<Long, Long> matureByAct = new HashMap<>();
        for (ReviewMode m : modes(mode)) {
            Pool modePool = poolFor(pool, m);
            merge(totalByAct, totalByAct(userId, modePool, m));
            merge(startedByAct, toMap(stateRepository.countStartedByAct(
                    userId, m, ANY_STABILITY, modePool.actIds(), modePool.sourceIds())));
            merge(matureByAct, toMap(stateRepository.countStartedByAct(
                    userId, m, MATURE_STABILITY_THRESHOLD, modePool.actIds(), modePool.sourceIds())));
        }

        List<LegalAct> acts = actRepository.findAllWithFieldByIdIn(pool.actIds()).stream()
                .sorted(Comparator
                        .comparingInt((LegalAct a) -> a.getLegalField().getSortOrder())
                        .thenComparingInt(LegalAct::getSortOrder)
                        .thenComparing(LegalAct::getName))
                .toList();
        return acts.stream().map(act -> {
            int total = totalByAct.getOrDefault(act.getId(), 0L).intValue();
            int started = startedByAct.getOrDefault(act.getId(), 0L).intValue();
            int mature = matureByAct.getOrDefault(act.getId(), 0L).intValue();
            double masteryPercentage = total == 0 ? 0 : (mature * 100.0) / total;
            return new ActMasteryResponse(
                    act.getId(), act.getName(), act.getShortName(),
                    act.getLegalField().getId(), act.getLegalField().getName(),
                    total, started, mature, masteryPercentage);
        }).toList();
    }

    /**
     * Pytania, które użytkownik najłatwiej zapomni: najniższa szansa przypomnienia w tej chwili.
     * Ranking liczymy z samych liczb (stabilność, ostatnia powtórka), a treść pobieramy dopiero
     * dla {@code limit} wybranych pytań. To samo pytanie w obu trybach pojawia się raz (z gorszym wynikiem).
     */
    @Transactional(readOnly = true)
    public List<WeakQuestionResponse> getWeakQuestions(Long actId, ReviewMode mode, int limit, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        Long userId = user.getId();
        Pool pool = poolService.forUser(userId);
        int safeLimit = Math.max(1, Math.min(limit, MAX_WEAK_QUESTIONS));
        LocalDateTime now = LocalDateTime.now(clock);

        record Ranked(StartedSummary summary, double retrievability) {
        }

        Map<Long, Ranked> worstPerQuestion = new HashMap<>();
        for (ReviewMode m : modes(mode)) {
            Pool modePool = poolFor(pool, m);
            for (StartedSummary s : stateRepository.findStartedSummaries(userId, m, modePool.actIds(), modePool.sourceIds())) {
                if (actId != null && !actId.equals(s.getLegalActId())) {
                    continue;
                }
                double elapsedDays = Duration.between(s.getLastReviewAt(), now).toMillis() / 86_400_000.0;
                Ranked ranked = new Ranked(s, fsrsEngine.retrievability(elapsedDays, s.getStability()));
                worstPerQuestion.merge(s.getQuestionId(), ranked,
                        (a, b) -> a.retrievability() <= b.retrievability() ? a : b);
            }
        }

        List<Ranked> weakest = worstPerQuestion.values().stream()
                .sorted(Comparator.comparingDouble(Ranked::retrievability).thenComparing(r -> r.summary().getQuestionId()))
                .limit(safeLimit)
                .toList();

        List<Long> ids = weakest.stream().map(r -> r.summary().getQuestionId()).toList();
        Map<Long, Question> questionById = questionRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        List<WeakQuestionResponse> result = new ArrayList<>();
        for (Ranked ranked : weakest) {
            Question question = questionById.get(ranked.summary().getQuestionId());
            if (question == null) continue;
            result.add(new WeakQuestionResponse(
                    question.getId(), question.getBody(), ranked.summary().getMode(), ranked.summary().getLapses(),
                    ranked.retrievability(), ranked.summary().getLastReviewAt()));
        }
        return result;
    }

    /**
     * Liczba odpowiedzi na każdy z ostatnich {@code days} dni (łącznie z dzisiejszym).
     * {@code mode} null = fiszki i testy razem, inaczej tylko wskazany tryb.
     */
    @Transactional(readOnly = true)
    public List<ActivityHeatmapItem> getActivity(int days, ReviewMode mode, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        int safeDays = Math.max(1, Math.min(days, MAX_ACTIVITY_DAYS));

        LocalDate today = LocalDate.now(clock);
        LocalDateTime since = today.minusDays(safeDays - 1L).atStartOfDay();

        List<LocalDateTime> times = mode == null
                ? reviewLogRepository.findReviewTimesSince(user.getId(), since)
                : reviewLogRepository.findReviewTimesSinceByMode(user.getId(), mode, since);
        return StatisticsMath.perDay(today, safeDays, times);
    }

    /**
     * Statystyki Testów: ile pytań jest do rozwiązania, ile już rozwiązano, skuteczność z ostatnich 30 dni
     * i aktywność z 28 dni. Pytania policzone są tak jak w katalogu Testów, więc liczby na pulpicie i w Testach się zgadzają.
     */
    @Transactional(readOnly = true)
    public TestStatsResponse getTestStats(Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        Long userId = user.getId();
        Pool pool = poolService.forUser(userId);

        LocalDate today = LocalDate.now(clock);
        LocalDateTime accuracySince = LocalDateTime.now(clock).minusDays(TEST_ACCURACY_WINDOW_DAYS);
        LocalDateTime activitySince = today.minusDays(TEST_ACTIVITY_DAYS - 1L).atStartOfDay();

        long total = StatisticsMath.sum(toMap(questionRepository.countBySource(pool.actIds(), pool.sourceIds())).values());
        long solved = StatisticsMath.sum(toMap(
                reviewLogRepository.countSolvedTestQuestionsBySource(userId, pool.actIds(), pool.sourceIds())).values());
        long answers = reviewLogRepository.countAnswersSince(userId, ReviewMode.TEST, accuracySince);
        long correct = reviewLogRepository.countCorrectAnswersSince(userId, ReviewMode.TEST, accuracySince);
        long confidentMistakes = reviewLogRepository.countConfidentMistakesSince(userId, ReviewMode.TEST, accuracySince);
        long luckyGuesses = reviewLogRepository.countLuckyGuessesSince(userId, ReviewMode.TEST, accuracySince);
        List<ActivityHeatmapItem> activity = StatisticsMath.perDay(
                today, TEST_ACTIVITY_DAYS,
                reviewLogRepository.findReviewTimesSinceByMode(userId, ReviewMode.TEST, activitySince));

        return new TestStatsResponse(
                (int) total, (int) solved, answers, correct, StatisticsMath.percentage(correct, answers),
                confidentMistakes, luckyGuesses, activity);
    }

    // ---------------------------------------------------------------- pomocnicze

    private static Set<ReviewMode> modes(ReviewMode mode) {
        return mode == null ? EnumSet.allOf(ReviewMode.class) : EnumSet.of(mode);
    }

    /** Pula trybu (akty ścieżki z zawężeniem, wszystkie źródła). */
    private static Pool poolFor(Pool pool, ReviewMode mode) {
        return mode == ReviewMode.FLASHCARD ? pool.forFlashcards() : pool;
    }

    /**
     * Ile pytań można teraz ćwiczyć w trybie: w testach wszystkie z puli, w fiszkach tylko odblokowane i z widoczną
     * fiszką. Liczone z odchudzonych widoków pytań, tak samo jak kolejki, więc zasada odblokowania jest w jednym miejscu.
     */
    private long countAvailable(Long userId, Pool modePool, ReviewMode mode, Long actId) {
        List<QuestionRef> refs = questionRepository.findRefsInPool(modePool.actIds(), modePool.sourceIds());
        Set<Long> unlocked = mode == ReviewMode.FLASHCARD ? unlockService.unlockedQuestionIds(userId) : Set.of();
        return refs.stream()
                .filter(ref -> actId == null || actId.equals(ref.getLegalActId()))
                .filter(ref -> mode == ReviewMode.TEST || QuestionScopes.isUnlocked(ref, unlocked))
                .count();
    }

    /** Pytania trybu w każdym akcie (groupId = numer aktu), tak jak {@link #countAvailable}. */
    private Map<Long, Long> totalByAct(Long userId, Pool modePool, ReviewMode mode) {
        if (mode == ReviewMode.TEST) {
            return toMap(questionRepository.countByAct(modePool.actIds(), modePool.sourceIds()));
        }
        return toMap(questionRepository.countUnlockedFlashcardsByAct(userId, modePool.actIds(), modePool.sourceIds()));
    }

    private static long sumFor(List<GroupCount> rows, Long actId) {
        long total = 0;
        for (GroupCount row : rows) {
            if (actId == null || actId.equals(row.getGroupId())) {
                total += row.getTotal() == null ? 0 : row.getTotal();
            }
        }
        return total;
    }

    private static void merge(Map<Long, Long> into, Map<Long, Long> from) {
        from.forEach((key, value) -> into.merge(key, value, Long::sum));
    }

    /**
     * Wskaźnik zapamiętywania: jaki odsetek prawdziwych powtórek z ostatnich 30 dni skończył się
     * przypomnieniem sobie pytania. Nie liczy pierwszego zobaczenia pytania ani powtórek tego samego dnia,
     * bo te nie mówią nic o tym, czy materiał się utrwala. Brak takich powtórek to {@code null} („brak danych"), a nie 0%.
     */
    private Double calculateRetentionRate(Long userId, Long actId, ReviewMode mode) {
        LocalDateTime since = LocalDateTime.now(clock).minusDays(RETENTION_WINDOW_DAYS);

        long all = 0;
        long successful = 0;
        for (ReviewMode m : modes(mode)) {
            if (actId != null) {
                all += reviewLogRepository.countRecallsInAct(userId, m, actId, since, RETENTION_MIN_ELAPSED_DAYS, 1);
                successful += reviewLogRepository.countRecallsInAct(userId, m, actId, since, RETENTION_MIN_ELAPSED_DAYS, 2);
            } else {
                all += reviewLogRepository.countRecalls(userId, m, since, RETENTION_MIN_ELAPSED_DAYS, 1);
                successful += reviewLogRepository.countRecalls(userId, m, since, RETENTION_MIN_ELAPSED_DAYS, 2);
            }
        }
        return StatisticsMath.percentage(successful, all);
    }

    private static List<DailyForecastItem> forecast(List<DueRow> due, LocalDate today) {
        Map<LocalDate, Long> countsByDate = due.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getDueDate().toLocalDate().isBefore(today) ? today : s.getDueDate().toLocalDate(),
                        Collectors.counting()));

        List<DailyForecastItem> forecast = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = today.plusDays(i);
            forecast.add(new DailyForecastItem(date, countsByDate.getOrDefault(date, 0L)));
        }
        return forecast;
    }

    static Map<Long, Long> toMap(List<GroupCount> rows) {
        Map<Long, Long> result = new HashMap<>();
        for (GroupCount row : rows) {
            result.put(row.getGroupId(), row.getTotal());
        }
        return result;
    }
}
