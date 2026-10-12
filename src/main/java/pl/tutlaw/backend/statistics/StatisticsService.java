package pl.tutlaw.backend.statistics;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import pl.tutlaw.backend.card.Card;
import pl.tutlaw.backend.card.CardRepository;
import pl.tutlaw.backend.chapter.Chapter;
import pl.tutlaw.backend.chapter.ChapterRepository;
import pl.tutlaw.backend.review.ReviewLog;
import pl.tutlaw.backend.review.ReviewLogRepository;
import pl.tutlaw.backend.review.UserCardState;
import pl.tutlaw.backend.review.UserCardStateRepository;
import pl.tutlaw.backend.review.fsrs.FsrsEngine;
import pl.tutlaw.backend.source.Source;
import pl.tutlaw.backend.source.SourceRepository;
import pl.tutlaw.backend.statistics.dto.*;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatisticsService {

    private static final double MATURE_STABILITY_THRESHOLD = 21.0;
    private static final int RETENTION_WINDOW_DAYS = 30;

    private final CardRepository cardRepository;
    private final ChapterRepository chapterRepository;
    private final SourceRepository sourceRepository;
    private final UserCardStateRepository stateRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final CurrentUserResolver currentUserResolver;
    private final FsrsEngine fsrsEngine;

    public OverviewResponse getOverview(Long subjectId, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);

        List<Card> cards = subjectId != null
                ? cardRepository.findBySource_Subject_Id(subjectId)
                : cardRepository.findAll();

        List<Long> cardIds = cards.stream().map(Card::getId).toList();
        List<UserCardState> states = stateRepository.findByUserIdAndCardIdIn(user.getId(), cardIds);

        int totalCards = cards.size();
        int startedCards = (int) states.stream().filter(s -> s.getReps() > 0).count();
        int matureCards = (int) states.stream().filter(s -> s.getStability() > MATURE_STABILITY_THRESHOLD).count();

        double retentionRate = calculateRetentionRate(user.getId(), subjectId);
        List<DailyForecastItem> forecast = calculateForecast(user.getId(), subjectId);

        return new OverviewResponse(totalCards, startedCards, matureCards, retentionRate, forecast);
    }

    public List<ChapterMasteryResponse> getMasteryBySource(Long sourceId, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);

        List<Chapter> chapters = chapterRepository.findBySourceIdOrderByOrderIndexAsc(sourceId);
        List<Card> cardsInSource = cardRepository.findBySourceId(sourceId);

        Map<Long, List<Card>> cardsByChapter = cardsInSource.stream()
                .filter(c -> c.getChapter() != null)
                .collect(Collectors.groupingBy(c -> c.getChapter().getId()));

        List<Long> cardIds = cardsInSource.stream().map(Card::getId).toList();
        Map<Long, UserCardState> stateByCardId = stateRepository
                .findByUserIdAndCardIdIn(user.getId(), cardIds)
                .stream()
                .collect(Collectors.toMap(s -> s.getCard().getId(), s -> s));

        return chapters.stream().map(chapter -> {
            List<Card> cardsInChapter = cardsByChapter.getOrDefault(chapter.getId(), List.of());
            int total = cardsInChapter.size();

            long started = cardsInChapter.stream()
                    .filter(c -> {
                        UserCardState s = stateByCardId.get(c.getId());
                        return s != null && s.getReps() > 0;
                    })
                    .count();

            long mature = cardsInChapter.stream()
                    .filter(c -> {
                        UserCardState s = stateByCardId.get(c.getId());
                        return s != null && s.getStability() > MATURE_STABILITY_THRESHOLD;
                    })
                    .count();

            double masteryPercentage = total == 0 ? 0 : (mature * 100.0) / total;
//            double masteryPercentage = 999.0;
            return new ChapterMasteryResponse(chapter.getId(), chapter.getTitle(), total, (int) started, (int) mature, masteryPercentage);
        }).toList();
    }

    public List<WeakCardResponse> getWeakCards(Long subjectId, int limit, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);

        List<Card> cards = subjectId != null
                ? cardRepository.findBySource_Subject_Id(subjectId)
                : cardRepository.findAll();

        Map<Long, Card> cardById = cards.stream().collect(Collectors.toMap(Card::getId, c -> c));
        List<Long> cardIds = cards.stream().map(Card::getId).toList();

        List<UserCardState> states = stateRepository.findByUserIdAndCardIdIn(user.getId(), cardIds);

        return states.stream()
                .filter(s -> s.getReps() > 0)
                .map(s -> {
                    double elapsedDays = Duration.between(s.getLastReviewAt(), LocalDateTime.now())
                            .toMillis() / 86_400_000.0;
                    double retrievability = fsrsEngine.retrievability(elapsedDays, s.getStability());
                    Card card = cardById.get(s.getCard().getId());
                    return new WeakCardResponse(
                            card.getId(), card.getFront(), s.getLapses(), retrievability, s.getLastReviewAt()
                    );
                })
                .sorted(Comparator.comparingDouble(WeakCardResponse::currentRetrievability))
                .limit(limit)
                .toList();
    }

    public List<ActivityHeatmapItem> getActivity(int days, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        List<ReviewLog> logs = reviewLogRepository.findByUserIdAndReviewedAtAfter(user.getId(), since);

        Map<LocalDate, Long> countsByDate = logs.stream()
                .collect(Collectors.groupingBy(r -> r.getReviewedAt().toLocalDate(), Collectors.counting()));

        List<ActivityHeatmapItem> result = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            result.add(new ActivityHeatmapItem(date, countsByDate.getOrDefault(date, 0L)));
        }
        return result;
    }

    public List<SourceProgressResponse> getSourceProgress(Long subjectId, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);

        List<Source> sources = sourceRepository.findBySubjectId(subjectId);
        List<Card> allCards = cardRepository.findBySource_Subject_Id(subjectId);

        Map<Long, List<Card>> cardsBySource = allCards.stream()
                .collect(Collectors.groupingBy(c -> c.getSource().getId()));

        List<Long> allCardIds = allCards.stream().map(Card::getId).toList();
        Map<Long, UserCardState> stateByCardId = stateRepository
                .findByUserIdAndCardIdIn(user.getId(), allCardIds)
                .stream()
                .collect(Collectors.toMap(s -> s.getCard().getId(), s -> s));

        return sources.stream().map(source -> {
            List<Card> cardsInSource = cardsBySource.getOrDefault(source.getId(), List.of());
            int total = cardsInSource.size();
            long started = cardsInSource.stream()
                    .filter(c -> {
                        UserCardState s = stateByCardId.get(c.getId());
                        return s != null && s.getReps() > 0;
                    })
                    .count();
            double percentage = total == 0 ? 0 : (started * 100.0) / total;
            return new SourceProgressResponse(source.getId(), source.getName(), source.getSourceType(), total, (int) started, percentage);
        }).toList();
    }

    private double calculateRetentionRate(Long userId, Long subjectId) {
        LocalDateTime since = LocalDateTime.now().minusDays(RETENTION_WINDOW_DAYS);
        List<ReviewLog> recentLogs = subjectId != null
                ? reviewLogRepository.findByUserIdAndSubjectIdSince(userId, subjectId, since)
                : reviewLogRepository.findByUserIdAndReviewedAtAfter(userId, since);

        if (recentLogs.isEmpty()) return 0;

        long successful = recentLogs.stream().filter(r -> r.getRating() > 1).count();
        return successful * 100.0 / recentLogs.size();
    }

    private List<DailyForecastItem> calculateForecast(Long userId, Long subjectId) {
        LocalDateTime now = LocalDateTime.now();
        List<UserCardState> dueStates = subjectId != null
                ? stateRepository.findDueBetween(userId, subjectId, now, now.plusDays(7))
                : stateRepository.findDueBetweenAnySubject(userId, now, now.plusDays(7));

        Map<LocalDate, Long> countsByDate = dueStates.stream()
                .collect(Collectors.groupingBy(s -> s.getDueDate().toLocalDate(), Collectors.counting()));

        List<DailyForecastItem> forecast = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = now.toLocalDate().plusDays(i);
            forecast.add(new DailyForecastItem(date, countsByDate.getOrDefault(date, 0L)));
        }
        return forecast;
    }
}