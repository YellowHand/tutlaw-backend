package pl.tutlaw.backend.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tutlaw.backend.exam.ExamAttemptRepository;
import pl.tutlaw.backend.pool.UserPoolActRepository;
import pl.tutlaw.backend.report.QuestionReportRepository;
import pl.tutlaw.backend.review.CustomTestSessionService;
import pl.tutlaw.backend.review.ReviewLogRepository;
import pl.tutlaw.backend.studytime.StudyTimeRepository;
import pl.tutlaw.backend.review.UserQuestionStateRepository;
import pl.tutlaw.backend.settings.UserSettingsRepository;
import pl.tutlaw.backend.flashcard.FlashcardAdditionRepository;
import pl.tutlaw.backend.track.TrackRepository;
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
    private final UserQuestionStateRepository stateRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final QuestionReportRepository questionReportRepository;
    private final TrackRepository trackRepository;
    private final UserPoolActRepository poolActRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final ExamAttemptRepository examAttemptRepository;
    private final FlashcardAdditionRepository flashcardAdditionRepository;
    private final CustomTestSessionService customTestSessions;
    private final StudyTimeRepository studyTimeRepository;
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

        List<AccountExportResponse.QuestionStateItem> states = stateRepository
                .findExportRows(user.getId()).stream()
                .map(s -> new AccountExportResponse.QuestionStateItem(
                        s.getQuestionId(), s.getMode().name(), s.getQuestionBody(), s.getState().name(),
                        orZero(s.getReps()), orZero(s.getLapses()),
                        orZero(s.getStability()), orZero(s.getDifficulty()),
                        s.getDueDate(), s.getLastReviewAt(), Boolean.TRUE.equals(s.getSuspended())))
                .toList();

        List<AccountExportResponse.ReviewItem> reviews = reviewLogRepository
                .findExportRows(user.getId()).stream()
                .map(r -> new AccountExportResponse.ReviewItem(
                        r.getQuestionId(), r.getReviewedAt(), r.getMode().name(), orZero(r.getRating()),
                        r.getWasCorrect(), Boolean.TRUE.equals(r.getWasGuessed())))
                .toList();

        List<AccountExportResponse.ReportItem> reports = questionReportRepository
                .findExportRows(user.getId()).stream()
                .map(r -> new AccountExportResponse.ReportItem(
                        r.getQuestionId(), r.getQuestionBody(), r.getReason().name(), r.getComment(),
                        r.getStatus().name(), r.getCreatedAt()))
                .toList();

        List<AccountExportResponse.ExamEntry> exams = examAttemptRepository.findAllForUser(user.getId()).stream()
                .map(a -> new AccountExportResponse.ExamEntry(
                        a.getFinishedAt(), a.getScopeLabel(), a.getQuestionCount(), a.getAnsweredCount(),
                        a.getCorrectCount(), a.getPassThresholdPercent(), a.getPassPoints(), a.isPassed(),
                        a.getTimeLimitMinutes(), a.getDurationSeconds(), a.isTimedOut()))
                .toList();

        List<AccountExportResponse.FlashcardAdditionItem> additions = flashcardAdditionRepository
                .findExportRows(user.getId()).stream()
                .map(a -> new AccountExportResponse.FlashcardAdditionItem(
                        a.getQuestionId(), a.getQuestionBody(), a.getAddedAt()))
                .toList();

        AccountExportResponse.LearningSettings settings = userSettingsRepository.findById(user.getId())
                .map(s -> new AccountExportResponse.LearningSettings(
                        s.getNewFlashcardsPerDay(), s.getNewTestQuestionsPerDay(), s.getMaxReviewsPerDay(),
                        s.getDesiredRetention(),
                        s.getTrackId() == null ? null : trackRepository.findById(s.getTrackId()).map(t -> t.getSlug()).orElse(null),
                        s.getTheme(),
                        poolActRepository.findActIdsByUserId(user.getId()),
                        s.isHintHiddenTest(), s.isHintHiddenFlashcard(), s.isQuestionTimerHidden()))
                .orElse(null);

        List<AccountExportResponse.CustomTestItem> customTests = customTestSessions.listAll(user.getId()).stream()
                .map(t -> new AccountExportResponse.CustomTestItem(
                        t.startedAt(), t.finishedAt(), t.scopeLabel(), t.kind().name(), t.requestedCount(),
                        t.questionCount(), t.answeredCount(), t.correctCount(), t.guessedCount(), t.timeSeconds()))
                .toList();

        List<AccountExportResponse.StudyTimeItem> studyTime = studyTimeRepository.findAllForUser(user.getId()).stream()
                .map(d -> new AccountExportResponse.StudyTimeItem(d.getStudyDay(), d.getSeconds()))
                .toList();

        return new AccountExportResponse(
                Instant.now(clock), account, states, reviews, reports, exams, additions, customTests, studyTime,
                settings);
    }

    /**
     * Usuwa konto razem z całą historią nauki. Baza kasuje powiązane wiersze sama (ON DELETE CASCADE w schemacie bazy).
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
