package pl.gradusiuris.backend.statistics;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gradusiuris.backend.exam.ExamAttemptRepository;
import pl.gradusiuris.backend.review.CustomTestSessionService;
import pl.gradusiuris.backend.review.ReviewLogRepository;
import pl.gradusiuris.backend.review.ReviewMode;
import pl.gradusiuris.backend.statistics.dto.LearningSummaryResponse;
import pl.gradusiuris.backend.studytime.StudyTimeService;
import pl.gradusiuris.backend.user.CurrentUserResolver;

/**
 * "Podsumowanie nauki" w Statystykach: ile odpowiedzi, egzaminów i własnych testów, ile czasu nauki oraz średni czas
 * odpowiedzi. Czas nauki (czas w aplikacji) i czas odpowiedzi na pytanie to dwie osobne miary.
 */
@Service
@RequiredArgsConstructor
public class LearningSummaryService {

    private final ReviewLogRepository reviewLogs;
    private final ExamAttemptRepository examAttempts;
    private final CustomTestSessionService customTests;
    private final StudyTimeService studyTime;
    private final CurrentUserResolver currentUserResolver;

    @Transactional(readOnly = true)
    public LearningSummaryResponse get(Authentication authentication) {
        Long userId = currentUserResolver.resolve(authentication).getId();
        long tests = reviewLogs.countByUserIdAndMode(userId, ReviewMode.TEST);
        long flashcards = reviewLogs.countByUserIdAndMode(userId, ReviewMode.FLASHCARD);
        StudyTimeService.Totals time = studyTime.totals(userId);
        return new LearningSummaryResponse(
                tests + flashcards, tests, flashcards,
                examAttempts.countByUserId(userId),
                customTests.countFinished(userId),
                time.todaySeconds(), time.last7DaysSeconds(), time.totalSeconds(),
                reviewLogs.averageAnswerSeconds(userId, ReviewMode.TEST),
                reviewLogs.averageAnswerSeconds(userId, ReviewMode.FLASHCARD));
    }
}
