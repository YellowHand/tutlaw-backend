package pl.tutlaw.backend.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tutlaw.backend.exam.ExamAttemptRepository;
import pl.tutlaw.backend.progress.ChapterProgressRepository;
import pl.tutlaw.backend.report.CardReportRepository;
import pl.tutlaw.backend.review.ReviewLogRepository;
import pl.tutlaw.backend.review.UserCardStateRepository;
import pl.tutlaw.backend.settings.UserSettingsRepository;
import pl.tutlaw.backend.user.dto.AccountExportResponse;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Dane konta w rozumieniu RODO: pobranie kopii danych i usunięcie konta. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final CurrentUserResolver currentUserResolver;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ChapterProgressRepository chapterProgressRepository;
    private final UserCardStateRepository stateRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final CardReportRepository cardReportRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final ExamAttemptRepository examAttemptRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AccountExportResponse export(Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);

        AccountExportResponse.Account account = new AccountExportResponse.Account(
                user.getPrivateEmail(),
                user.getUniversityEmail(),
                user.isUniversityEmailVerified(),
                user.getRole().name(),
                user.getCreatedAt(),
                user.getTermsAcceptedAt());

        List<AccountExportResponse.ChapterProgressItem> progress = chapterProgressRepository
                .findExportRows(user.getId()).stream()
                .map(p -> new AccountExportResponse.ChapterProgressItem(
                        p.getChapterId(), p.getChapterTitle(), Boolean.TRUE.equals(p.getReadFlag()), p.getReadAt()))
                .toList();

        List<AccountExportResponse.CardStateItem> states = stateRepository
                .findExportRows(user.getId()).stream()
                .map(s -> new AccountExportResponse.CardStateItem(
                        s.getCardId(), s.getCardFront(), s.getState().name(),
                        orZero(s.getReps()), orZero(s.getLapses()),
                        orZero(s.getStability()), orZero(s.getDifficulty()),
                        s.getDueDate(), s.getLastReviewAt(), Boolean.TRUE.equals(s.getSuspended())))
                .toList();

        List<AccountExportResponse.ReviewItem> reviews = reviewLogRepository
                .findExportRows(user.getId()).stream()
                .map(r -> new AccountExportResponse.ReviewItem(
                        r.getCardId(), r.getReviewedAt(), r.getMode().name(), orZero(r.getRating()),
                        r.getWasCorrect(), Boolean.TRUE.equals(r.getWasGuessed())))
                .toList();

        List<AccountExportResponse.ReportItem> reports = cardReportRepository
                .findExportRows(user.getId()).stream()
                .map(r -> new AccountExportResponse.ReportItem(
                        r.getCardId(), r.getCardFront(), r.getReason().name(), r.getComment(),
                        r.getStatus().name(), r.getCreatedAt()))
                .toList();

        List<AccountExportResponse.ExamEntry> exams = examAttemptRepository.findAllForUser(user.getId()).stream()
                .map(a -> new AccountExportResponse.ExamEntry(
                        a.getFinishedAt(), a.getScopeLabel(), a.getQuestionCount(), a.getAnsweredCount(),
                        a.getCorrectCount(), a.getPassThresholdPercent(), a.isPassed(),
                        a.getTimeLimitMinutes(), a.getDurationSeconds(), a.isTimedOut()))
                .toList();

        AccountExportResponse.LearningSettings settings = userSettingsRepository.findById(user.getId())
                .map(s -> new AccountExportResponse.LearningSettings(
                        s.getNewCardsPerDay(), s.getNewTestQuestionsPerDay(), s.getMaxReviewsPerDay(),
                        s.getDesiredRetention(), s.getExamDate(), s.isShuffleOptions()))
                .orElse(null);

        return new AccountExportResponse(
                Instant.now(clock), account, progress, states, reviews, reports, exams, settings);
    }

    /**
     * Usuwa konto razem z całą historią nauki. Baza kasuje powiązane wiersze sama (ON DELETE CASCADE, migracja V11).
     * Złe hasło to IllegalArgumentException (HTTP 400), a konto zostaje nietknięte.
     */
    @Transactional
    public void deleteAccount(Authentication authentication, String password) {
        User user = currentUserResolver.resolve(authentication);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            log.warn("Account deletion refused for user {}: wrong password", user.getId());
            throw new IllegalArgumentException("Nieprawidłowe hasło");
        }
        userRepository.delete(user);
        log.info("Account {} deleted at the user's request", user.getId());
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static double orZero(Double value) {
        return value == null ? 0.0 : value;
    }
}
