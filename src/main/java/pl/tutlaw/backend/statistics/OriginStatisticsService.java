package pl.tutlaw.backend.statistics;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import pl.tutlaw.backend.card.CardRepository;
import pl.tutlaw.backend.review.ReviewLogRepository;
import pl.tutlaw.backend.review.UserCardStateRepository;
import pl.tutlaw.backend.statistics.dto.OriginStatsResponse;
import pl.tutlaw.backend.user.CurrentUserResolver;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Statystyki Testów według pochodzenia pytań i roku egzaminu (zakładka „Statystyki"). Nie uwzględnia filtra
 * pochodzenia z Testów: zawsze pokazuje wszystkie pochodzenia.
 */
@Service
@RequiredArgsConstructor
public class OriginStatisticsService {

    /** Próg utrwalenia taki sam jak „Opanowane" w {@link StatisticsService}. */
    static final double MATURE_STABILITY_THRESHOLD = 21.0;
    static final int RECENT_WINDOW_DAYS = 30;
    /** Początek „od zawsze" dla zapytania o odpowiedzi (aplikacja jest nowsza). */
    private static final LocalDateTime ALL_TIME_SINCE = LocalDateTime.of(2000, 1, 1, 0, 0);

    private final CardRepository cardRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final UserCardStateRepository stateRepository;
    private final CurrentUserResolver currentUserResolver;
    private final Clock clock;

    public OriginStatsResponse getOriginStats(Authentication authentication) {
        Long userId = currentUserResolver.resolve(authentication).getId();
        LocalDateTime now = LocalDateTime.now(clock);

        return OriginStatisticsMath.assemble(
                cardRepository.countReadableTestQuestionsByOriginYear(userId),
                reviewLogRepository.countSolvedTestQuestionsByOriginYear(userId),
                stateRepository.countTestStatesByOriginYear(userId, now, MATURE_STABILITY_THRESHOLD),
                reviewLogRepository.countTestAnswersByOriginYear(userId, ALL_TIME_SINCE),
                reviewLogRepository.countTestAnswersByOriginYear(userId, now.minusDays(RECENT_WINDOW_DAYS)));
    }
}
