package pl.tutlaw.backend.statistics;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tutlaw.backend.pool.Pool;
import pl.tutlaw.backend.pool.PoolService;
import pl.tutlaw.backend.question.QuestionRepository;
import pl.tutlaw.backend.review.ReviewLogRepository;
import pl.tutlaw.backend.review.UserQuestionStateRepository;
import pl.tutlaw.backend.source.QuestionSourceRepository;
import pl.tutlaw.backend.statistics.SourceStatisticsMath.SourceInfo;
import pl.tutlaw.backend.statistics.dto.SourceStatsResponse;
import pl.tutlaw.backend.user.CurrentUserResolver;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

/**
 * Statystyki Testów według źródła pytań (zakładka „Statystyki"). Nie uwzględnia filtra źródeł z Testów ani zawężenia
 * do aktów: zawsze pokazuje wszystkie źródła w aktach ścieżki użytkownika.
 */
@Service
@RequiredArgsConstructor
public class SourceStatisticsService {

    /** Próg utrwalenia taki sam jak „Opanowane" w {@link StatisticsService}. */
    static final double MATURE_STABILITY_THRESHOLD = 21.0;
    static final int RECENT_WINDOW_DAYS = 30;
    /** Początek „od zawsze" dla zapytania o odpowiedzi (aplikacja jest nowsza). */
    private static final LocalDateTime ALL_TIME_SINCE = LocalDateTime.of(2000, 1, 1, 0, 0);

    private final QuestionRepository questionRepository;
    private final QuestionSourceRepository sourceRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final UserQuestionStateRepository stateRepository;
    private final CurrentUserResolver currentUserResolver;
    private final PoolService poolService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public SourceStatsResponse getSourceStats(Authentication authentication) {
        Long userId = currentUserResolver.resolve(authentication).getId();
        LocalDateTime now = LocalDateTime.now(clock);
        // Całe akty ścieżki i wszystkie źródła, bez zawężenia i filtra.
        Pool pool = poolService.forUser(userId).unnarrowed().forFlashcards();

        List<SourceInfo> sources = sourceRepository.findAllRows().stream()
                .map(s -> new SourceInfo(s.getId(), s.getName(), s.getKind(), s.getExamYear()))
                .toList();

        return SourceStatisticsMath.assemble(
                sources,
                new HashSet<>(pool.ownSheetIds()),
                StatisticsService.toMap(questionRepository.countBySource(pool.actIds(), pool.sourceIds())),
                StatisticsService.toMap(
                        reviewLogRepository.countSolvedTestQuestionsBySource(userId, pool.actIds(), pool.sourceIds())),
                stateRepository.countTestStatesBySource(
                        userId, now, MATURE_STABILITY_THRESHOLD, pool.actIds(), pool.sourceIds()),
                reviewLogRepository.countTestAnswersBySource(userId, ALL_TIME_SINCE, pool.actIds(), pool.sourceIds()),
                reviewLogRepository.countTestAnswersBySource(
                        userId, now.minusDays(RECENT_WINDOW_DAYS), pool.actIds(), pool.sourceIds()));
    }
}
