package pl.tutlaw.backend.statistics;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import pl.tutlaw.backend.card.Card;
import pl.tutlaw.backend.card.CardRepository;
import pl.tutlaw.backend.card.CardScopes;
import pl.tutlaw.backend.chapter.Chapter;
import pl.tutlaw.backend.chapter.ChapterRepository;
import pl.tutlaw.backend.common.GroupCount;
import pl.tutlaw.backend.flashcard.FlashcardUnlockService;
import pl.tutlaw.backend.review.ReviewLogRepository;
import pl.tutlaw.backend.review.ReviewMode;
import pl.tutlaw.backend.review.UserCardState;
import pl.tutlaw.backend.review.UserCardStateRepository;
import pl.tutlaw.backend.review.UserCardStateRepository.StartedCardSummary;
import pl.tutlaw.backend.review.fsrs.FsrsEngine;
import pl.tutlaw.backend.source.Source;
import pl.tutlaw.backend.source.SourceRepository;
import pl.tutlaw.backend.statistics.dto.*;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatisticsService {

    private static final double MATURE_STABILITY_THRESHOLD = 21.0;
    /** Dowolna stabilność: do zliczania wszystkich rozpoczętych kart. */
    private static final double ANY_STABILITY = -1.0;
    private static final int RETENTION_WINDOW_DAYS = 30;
    /** Wskaźnik zapamiętywania liczymy z powtórek po co najmniej tylu dniach od poprzedniej odpowiedzi. */
    private static final double RETENTION_MIN_ELAPSED_DAYS = 1.0;
    private static final int MAX_ACTIVITY_DAYS = 365;
    private static final int MAX_WEAK_CARDS = 100;
    private static final int TEST_ACCURACY_WINDOW_DAYS = 30;
    private static final int TEST_ACTIVITY_DAYS = 28;

    private final CardRepository cardRepository;
    private final ChapterRepository chapterRepository;
    private final SourceRepository sourceRepository;
    private final UserCardStateRepository stateRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final CurrentUserResolver currentUserResolver;
    private final FsrsEngine fsrsEngine;
    private final Clock clock;
    /** Które pytania mają odblokowaną fiszkę (pewny błąd albo ręczne dodanie do fiszek). */
    private final FlashcardUnlockService unlockService;

    /**
     * Przegląd nauki. „Wszystkie karty" to karty, które użytkownik może teraz ćwiczyć
     * (pule płaskie i przeczytane rozdziały podręczników), a nie cała biblioteka, inaczej pasek postępu
     * nigdy by się nie domykał przy rozdziałach, których jeszcze nie odblokował.
     * {@code mode}: FLASHCARD to tylko fiszki (bez pytań testowych i bez zablokowanych fiszek powiązanych z pytaniem),
     * TEST to tylko pytania testowe, a brak trybu (null) to cały materiał, czyli jedno i drugie.
     */
    public OverviewResponse getOverview(Long subjectId, ReviewMode mode, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        Long userId = user.getId();
        boolean withFlashcards = mode != ReviewMode.TEST;
        boolean withTests = mode != ReviewMode.FLASHCARD;

        long totalCards = countReadable(userId, subjectId, withFlashcards, withTests);
        long startedCards;
        long matureCards;
        if (subjectId != null) {
            startedCards = stateRepository.countStartedReadableInSubject(
                    userId, subjectId, ANY_STABILITY, withFlashcards, withTests);
            matureCards = stateRepository.countStartedReadableInSubject(
                    userId, subjectId, MATURE_STABILITY_THRESHOLD, withFlashcards, withTests);
        } else {
            startedCards = stateRepository.countStartedReadable(userId, ANY_STABILITY, withFlashcards, withTests);
            matureCards = stateRepository.countStartedReadable(
                    userId, MATURE_STABILITY_THRESHOLD, withFlashcards, withTests);
        }

        Double retentionRate = calculateRetentionRate(userId, subjectId, withFlashcards, withTests);
        List<DailyForecastItem> forecast = calculateForecast(userId, subjectId, withFlashcards, withTests);

        return new OverviewResponse((int) totalCards, (int) startedCards, (int) matureCards, retentionRate, forecast);
    }

    /**
     * Ile kart wybranego rodzaju użytkownik może teraz ćwiczyć. Liczone z odchudzonych widoków kart, tak samo jak
     * kolejki, więc zasada „fiszka powiązana z pytaniem jest ukryta do pierwszego pewnego błędu" jest w jednym miejscu.
     */
    private long countReadable(Long userId, Long subjectId, boolean withFlashcards, boolean withTests) {
        List<CardRepository.CardRef> readable = CardScopes.readableRefs(cardRepository, null, subjectId, null, userId);
        Set<Long> unlocked = withFlashcards && CardScopes.anyLinked(readable)
                ? unlockService.unlockedQuestionIds(userId)
                : Set.of();
        return readable.stream()
                .filter(ref -> CardScopes.isTest(ref) ? withTests : withFlashcards && CardScopes.isUnlocked(ref, unlocked))
                .count();
    }

    /** Opanowanie rozdziałów źródła. {@code mode}: FLASHCARD, TEST albo null (fiszki i testy razem). */
    public List<ChapterMasteryResponse> getMasteryBySource(Long sourceId, ReviewMode mode, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        boolean withFlashcards = mode != ReviewMode.TEST;
        boolean withTests = mode != ReviewMode.FLASHCARD;

        List<Chapter> chapters = chapterRepository.findBySourceIdOrderByOrderIndexAsc(sourceId);
        Map<Long, Long> totalByChapter = toMap(
                cardRepository.countByChapterForSource(sourceId, user.getId(), withFlashcards, withTests));
        Map<Long, Long> startedByChapter = toMap(stateRepository.countStartedByChapter(
                user.getId(), sourceId, ANY_STABILITY, withFlashcards, withTests));
        Map<Long, Long> matureByChapter = toMap(stateRepository.countStartedByChapter(
                user.getId(), sourceId, MATURE_STABILITY_THRESHOLD, withFlashcards, withTests));

        return chapters.stream().map(chapter -> {
            int total = totalByChapter.getOrDefault(chapter.getId(), 0L).intValue();
            int started = startedByChapter.getOrDefault(chapter.getId(), 0L).intValue();
            int mature = matureByChapter.getOrDefault(chapter.getId(), 0L).intValue();
            double masteryPercentage = total == 0 ? 0 : (mature * 100.0) / total;
            return new ChapterMasteryResponse(chapter.getId(), chapter.getTitle(), total, started, mature, masteryPercentage);
        }).toList();
    }

    /**
     * Karty, które użytkownik najłatwiej zapomni: najniższa szansa przypomnienia w tej chwili.
     * Ranking liczymy z samych liczb (stabilność, ostatnia powtórka), a treść pobieramy dopiero
     * dla {@code limit} wybranych kart.
     */
    public List<WeakCardResponse> getWeakCards(Long subjectId, int limit, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        int safeLimit = Math.max(1, Math.min(limit, MAX_WEAK_CARDS));
        LocalDateTime now = LocalDateTime.now(clock);

        List<StartedCardSummary> summaries = subjectId != null
                ? stateRepository.findStartedSummariesInSubject(user.getId(), subjectId)
                : stateRepository.findStartedSummaries(user.getId());

        record Ranked(StartedCardSummary summary, double retrievability) {
        }

        List<Ranked> weakest = summaries.stream()
                .map(s -> {
                    double elapsedDays = Duration.between(s.getLastReviewAt(), now).toMillis() / 86_400_000.0;
                    return new Ranked(s, fsrsEngine.retrievability(elapsedDays, s.getStability()));
                })
                .sorted(Comparator.comparingDouble(Ranked::retrievability))
                .limit(safeLimit)
                .toList();

        List<Long> ids = weakest.stream().map(r -> r.summary().getCardId()).toList();
        Map<Long, Card> cardById = cardRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Card::getId, c -> c));

        List<WeakCardResponse> result = new ArrayList<>();
        for (Ranked ranked : weakest) {
            Card card = cardById.get(ranked.summary().getCardId());
            if (card == null) continue;
            result.add(new WeakCardResponse(
                    card.getId(), card.getFront(), ranked.summary().getLapses(),
                    ranked.retrievability(), ranked.summary().getLastReviewAt()));
        }
        return result;
    }

    /**
     * Liczba odpowiedzi na każdy z ostatnich {@code days} dni (łącznie z dzisiejszym).
     * {@code mode} null = fiszki i testy razem, inaczej tylko wskazany tryb.
     */
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
    public TestStatsResponse getTestStats(Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        Long userId = user.getId();

        LocalDate today = LocalDate.now(clock);
        LocalDateTime accuracySince = LocalDateTime.now(clock).minusDays(TEST_ACCURACY_WINDOW_DAYS);
        LocalDateTime activitySince = today.minusDays(TEST_ACTIVITY_DAYS - 1L).atStartOfDay();

        long total = StatisticsMath.sum(toMap(cardRepository.countReadableTestQuestionsBySource(userId)).values());
        long solved = StatisticsMath.sum(toMap(reviewLogRepository.countSolvedTestQuestionsBySource(userId)).values());
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

    /**
     * Postęp w źródłach przedmiotu. Tu mianownikiem jest cała zawartość źródła (także nieprzeczytane
     * rozdziały podręcznika), bo pytanie brzmi „ile z całej książki już umiem".
     */
    public List<SourceProgressResponse> getSourceProgress(Long subjectId, ReviewMode mode, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        boolean withFlashcards = mode != ReviewMode.TEST;
        boolean withTests = mode != ReviewMode.FLASHCARD;

        List<Source> sources = sourceRepository.findBySubjectId(subjectId).stream()
                .sorted(Comparator.comparing(Source::getId))
                .toList();
        Map<Long, Long> totalBySource = toMap(
                cardRepository.countBySourceForSubject(subjectId, user.getId(), withFlashcards, withTests));
        Map<Long, Long> startedBySource = toMap(
                stateRepository.countStartedBySource(user.getId(), subjectId, withFlashcards, withTests));

        return sources.stream().map(source -> {
            int total = totalBySource.getOrDefault(source.getId(), 0L).intValue();
            int started = startedBySource.getOrDefault(source.getId(), 0L).intValue();
            double percentage = total == 0 ? 0 : (started * 100.0) / total;
            return new SourceProgressResponse(source.getId(), source.getName(), source.getSourceType(), total, started, percentage);
        }).toList();
    }

    /**
     * Wskaźnik zapamiętywania: jaki odsetek prawdziwych powtórek z ostatnich 30 dni skończył się
     * przypomnieniem sobie karty. Nie liczy pierwszego zobaczenia karty ani powtórek tego samego dnia,
     * bo te nie mówią nic o tym, czy materiał się utrwala (wcześniej zawyżały wynik).
     * Brak takich powtórek to {@code null} („brak danych"), a nie 0%.
     */
    private Double calculateRetentionRate(Long userId, Long subjectId, boolean withFlashcards, boolean withTests) {
        LocalDateTime since = LocalDateTime.now(clock).minusDays(RETENTION_WINDOW_DAYS);

        long all;
        long successful;
        if (subjectId != null) {
            all = reviewLogRepository.countRecallsInSubject(
                    userId, subjectId, since, RETENTION_MIN_ELAPSED_DAYS, 1, withFlashcards, withTests);
            successful = reviewLogRepository.countRecallsInSubject(
                    userId, subjectId, since, RETENTION_MIN_ELAPSED_DAYS, 2, withFlashcards, withTests);
        } else {
            all = reviewLogRepository.countRecalls(
                    userId, since, RETENTION_MIN_ELAPSED_DAYS, 1, withFlashcards, withTests);
            successful = reviewLogRepository.countRecalls(
                    userId, since, RETENTION_MIN_ELAPSED_DAYS, 2, withFlashcards, withTests);
        }

        return StatisticsMath.percentage(successful, all);
    }

    private List<DailyForecastItem> calculateForecast(
            Long userId, Long subjectId, boolean withFlashcards, boolean withTests) {
        LocalDate today = LocalDate.now(clock);
        // Bez dolnej granicy: karty z minionym terminem (także z wcześniejszych dni) liczą się do „dziś".
        LocalDateTime end = today.plusDays(7).atStartOfDay();
        List<UserCardState> dueStates = subjectId != null
                ? stateRepository.findDueUntil(userId, subjectId, end, withFlashcards, withTests)
                : stateRepository.findDueUntilAnySubject(userId, end, withFlashcards, withTests);

        Map<LocalDate, Long> countsByDate = dueStates.stream()
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

    private static Map<Long, Long> toMap(List<GroupCount> rows) {
        Map<Long, Long> result = new HashMap<>();
        for (GroupCount row : rows) {
            result.put(row.getGroupId(), row.getTotal());
        }
        return result;
    }
}
