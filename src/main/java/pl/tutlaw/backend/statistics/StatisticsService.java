package pl.tutlaw.backend.statistics;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import pl.tutlaw.backend.card.Card;
import pl.tutlaw.backend.card.CardRepository;
import pl.tutlaw.backend.chapter.Chapter;
import pl.tutlaw.backend.chapter.ChapterRepository;
import pl.tutlaw.backend.common.GroupCount;
import pl.tutlaw.backend.review.ReviewLogRepository;
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

    private final CardRepository cardRepository;
    private final ChapterRepository chapterRepository;
    private final SourceRepository sourceRepository;
    private final UserCardStateRepository stateRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final CurrentUserResolver currentUserResolver;
    private final FsrsEngine fsrsEngine;
    private final Clock clock;

    /**
     * Przegląd nauki. „Wszystkie karty" to karty, które użytkownik może teraz ćwiczyć
     * (pule płaskie i przeczytane rozdziały podręczników), a nie cała biblioteka, inaczej pasek postępu
     * nigdy by się nie domykał przy rozdziałach, których jeszcze nie odblokował.
     */
    public OverviewResponse getOverview(Long subjectId, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        Long userId = user.getId();

        long totalCards;
        long startedCards;
        long matureCards;
        if (subjectId != null) {
            totalCards = cardRepository.countReadableForUserAndSubject(subjectId, userId);
            startedCards = stateRepository.countStartedReadableInSubject(userId, subjectId, ANY_STABILITY);
            matureCards = stateRepository.countStartedReadableInSubject(userId, subjectId, MATURE_STABILITY_THRESHOLD);
        } else {
            totalCards = cardRepository.countReadableForUser(userId);
            startedCards = stateRepository.countStartedReadable(userId, ANY_STABILITY);
            matureCards = stateRepository.countStartedReadable(userId, MATURE_STABILITY_THRESHOLD);
        }

        double retentionRate = calculateRetentionRate(userId, subjectId);
        List<DailyForecastItem> forecast = calculateForecast(userId, subjectId);

        return new OverviewResponse((int) totalCards, (int) startedCards, (int) matureCards, retentionRate, forecast);
    }

    public List<ChapterMasteryResponse> getMasteryBySource(Long sourceId, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);

        List<Chapter> chapters = chapterRepository.findBySourceIdOrderByOrderIndexAsc(sourceId);
        Map<Long, Long> totalByChapter = toMap(cardRepository.countByChapterForSource(sourceId));
        Map<Long, Long> startedByChapter = toMap(
                stateRepository.countStartedByChapter(user.getId(), sourceId, ANY_STABILITY));
        Map<Long, Long> matureByChapter = toMap(
                stateRepository.countStartedByChapter(user.getId(), sourceId, MATURE_STABILITY_THRESHOLD));

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

    /** Liczba odpowiedzi na każdy z ostatnich {@code days} dni (łącznie z dzisiejszym). */
    public List<ActivityHeatmapItem> getActivity(int days, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        int safeDays = Math.max(1, Math.min(days, MAX_ACTIVITY_DAYS));

        LocalDate today = LocalDate.now(clock);
        LocalDateTime since = today.minusDays(safeDays - 1L).atStartOfDay();

        Map<LocalDate, Long> countsByDate = reviewLogRepository.findReviewTimesSince(user.getId(), since).stream()
                .collect(Collectors.groupingBy(LocalDateTime::toLocalDate, Collectors.counting()));

        List<ActivityHeatmapItem> result = new ArrayList<>();
        for (int i = safeDays - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            result.add(new ActivityHeatmapItem(date, countsByDate.getOrDefault(date, 0L)));
        }
        return result;
    }

    /**
     * Postęp w źródłach przedmiotu. Tu mianownikiem jest cała zawartość źródła (także nieprzeczytane
     * rozdziały podręcznika), bo pytanie brzmi „ile z całej książki już umiem".
     */
    public List<SourceProgressResponse> getSourceProgress(Long subjectId, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);

        List<Source> sources = sourceRepository.findBySubjectId(subjectId).stream()
                .sorted(Comparator.comparing(Source::getId))
                .toList();
        Map<Long, Long> totalBySource = toMap(cardRepository.countBySourceForSubject(subjectId));
        Map<Long, Long> startedBySource = toMap(stateRepository.countStartedBySource(user.getId(), subjectId));

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
     * Brak takich powtórek to 0.
     */
    private double calculateRetentionRate(Long userId, Long subjectId) {
        LocalDateTime since = LocalDateTime.now(clock).minusDays(RETENTION_WINDOW_DAYS);

        long all;
        long successful;
        if (subjectId != null) {
            all = reviewLogRepository.countRecallsInSubject(userId, subjectId, since, RETENTION_MIN_ELAPSED_DAYS, 1);
            successful = reviewLogRepository.countRecallsInSubject(userId, subjectId, since, RETENTION_MIN_ELAPSED_DAYS, 2);
        } else {
            all = reviewLogRepository.countRecalls(userId, since, RETENTION_MIN_ELAPSED_DAYS, 1);
            successful = reviewLogRepository.countRecalls(userId, since, RETENTION_MIN_ELAPSED_DAYS, 2);
        }

        if (all == 0) return 0;
        return successful * 100.0 / all;
    }

    private List<DailyForecastItem> calculateForecast(Long userId, Long subjectId) {
        LocalDate today = LocalDate.now(clock);
        // Bez dolnej granicy: karty z minionym terminem (także z wcześniejszych dni) liczą się do „dziś".
        LocalDateTime end = today.plusDays(7).atStartOfDay();
        List<UserCardState> dueStates = subjectId != null
                ? stateRepository.findDueUntil(userId, subjectId, end)
                : stateRepository.findDueUntilAnySubject(userId, end);

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
