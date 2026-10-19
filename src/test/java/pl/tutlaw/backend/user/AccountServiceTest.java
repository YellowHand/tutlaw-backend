package pl.tutlaw.backend.user;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import pl.tutlaw.backend.common.exception.UnauthorizedException;
import pl.tutlaw.backend.exam.ExamAttempt;
import pl.tutlaw.backend.exam.ExamAttemptRepository;
import pl.tutlaw.backend.flashcard.FlashcardAdditionRepository;
import pl.tutlaw.backend.pool.UserPoolActRepository;
import pl.tutlaw.backend.report.QuestionReportReason;
import pl.tutlaw.backend.report.QuestionReportRepository;
import pl.tutlaw.backend.report.QuestionReportStatus;
import pl.tutlaw.backend.review.CustomTestKind;
import pl.tutlaw.backend.review.CustomTestSessionService;
import pl.tutlaw.backend.review.LearningState;
import pl.tutlaw.backend.review.ReviewLogRepository;
import pl.tutlaw.backend.review.ReviewMode;
import pl.tutlaw.backend.review.UserQuestionStateRepository;
import pl.tutlaw.backend.review.dto.CustomTestSessionSummaryResponse;
import pl.tutlaw.backend.settings.Theme;
import pl.tutlaw.backend.settings.UserSettings;
import pl.tutlaw.backend.settings.UserSettingsRepository;
import pl.tutlaw.backend.studytime.StudyTimeDaily;
import pl.tutlaw.backend.studytime.StudyTimeRepository;
import pl.tutlaw.backend.testsupport.FakeRepos;
import pl.tutlaw.backend.testsupport.MutableClock;
import pl.tutlaw.backend.track.Track;
import pl.tutlaw.backend.track.TrackKind;
import pl.tutlaw.backend.track.TrackRepository;
import pl.tutlaw.backend.user.dto.AccountExportResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Eksport i usuwanie konta na prawdziwym AccountService, z repozytoriami "w pamięci". */
class AccountServiceTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 10, 12, 9, 30);

    private static final class Fixture {
        final List<User> users = new ArrayList<>();
        final PasswordEncoder encoder = new PasswordEncoder() {
            @Override
            public String encode(CharSequence raw) {
                return "hash:" + raw;
            }

            @Override
            public boolean matches(CharSequence raw, String encoded) {
                return ("hash:" + raw).equals(encoded);
            }
        };
        final AccountService service;
        final Authentication jan;
        /** Zapisane ustawienia nauki użytkownika; {@code null} = niczego nie ustawiał. */
        UserSettings storedSettings;
        /** Akty, do których użytkownik zawęził pulę. */
        List<Long> poolActIds = List.of();

        Fixture() {
            User user = new User();
            user.setId(1L);
            user.setPrivateEmail("jan@gmail.com");
            user.setUniversityEmail("jan@uni.pl");
            user.setPasswordHash(encoder.encode("haslo1234"));
            user.setUniversityEmailVerified(true);
            user.setActive(true);
            user.setCreatedAt(T.minusDays(30));
            user.setTermsAcceptedAt(T.minusDays(30));
            users.add(user);
            jan = FakeRepos.authentication("jan@gmail.com");

            UserQuestionStateRepository states = FakeRepos.proxy(UserQuestionStateRepository.class, (method, args) -> {
                assertThat(method).isEqualTo("findExportRows");
                assertThat(args[0]).isEqualTo(1L);
                return List.of(new UserQuestionStateRepository.ExportedState() {
                    public Long getQuestionId() { return 10L; }
                    public ReviewMode getMode() { return ReviewMode.FLASHCARD; }
                    public String getQuestionBody() { return "Co to jest dolus?"; }
                    public LearningState getState() { return LearningState.REVIEW; }
                    public Integer getReps() { return 4; }
                    public Integer getLapses() { return null; }
                    public Double getStability() { return 12.5; }
                    public Double getDifficulty() { return null; }
                    public LocalDateTime getDueDate() { return T.plusDays(12); }
                    public LocalDateTime getLastReviewAt() { return T.minusDays(1); }
                    public Boolean getSuspended() { return true; }
                }, new UserQuestionStateRepository.ExportedState() {
                    public Long getQuestionId() { return 10L; }
                    public ReviewMode getMode() { return ReviewMode.TEST; }
                    public String getQuestionBody() { return "Co to jest dolus?"; }
                    public LearningState getState() { return LearningState.LEARNING; }
                    public Integer getReps() { return 1; }
                    public Integer getLapses() { return 0; }
                    public Double getStability() { return 2.0; }
                    public Double getDifficulty() { return 5.5; }
                    public LocalDateTime getDueDate() { return T.plusDays(1); }
                    public LocalDateTime getLastReviewAt() { return T; }
                    public Boolean getSuspended() { return null; }
                });
            });

            ReviewLogRepository logs = FakeRepos.proxy(ReviewLogRepository.class, (method, args) -> {
                assertThat(method).isEqualTo("findExportRows");
                return List.of(new ReviewLogRepository.ExportedReview() {
                    public Long getQuestionId() { return 10L; }
                    public LocalDateTime getReviewedAt() { return T.minusDays(1); }
                    public ReviewMode getMode() { return ReviewMode.TEST; }
                    public Integer getRating() { return 3; }
                    public Boolean getWasCorrect() { return true; }
                    public Boolean getWasGuessed() { return null; }
                });
            });

            QuestionReportRepository reports = FakeRepos.proxy(QuestionReportRepository.class, (method, args) -> {
                assertThat(method).isEqualTo("findExportRows");
                assertThat(args[0]).isEqualTo(1L);
                return List.of(new QuestionReportRepository.ExportedReport() {
                    public Long getQuestionId() { return 10L; }
                    public String getQuestionBody() { return "Co to jest dolus?"; }
                    public QuestionReportReason getReason() { return QuestionReportReason.OUTDATED; }
                    public String getComment() { return "Przepis się zmienił"; }
                    public QuestionReportStatus getStatus() { return QuestionReportStatus.NEW; }
                    public LocalDateTime getCreatedAt() { return T.minusHours(3); }
                });
            });

            UserSettingsRepository settings = FakeRepos.proxy(UserSettingsRepository.class, (method, args) -> {
                assertThat(method).isEqualTo("findById");
                assertThat(args[0]).isEqualTo(1L);
                return Optional.ofNullable(storedSettings);
            });

            TrackRepository tracks = FakeRepos.proxy(TrackRepository.class, (method, args) -> {
                assertThat(method).isEqualTo("findById");
                Track notarial = new Track();
                notarial.setId(4L);
                notarial.setSlug("notarialna");
                notarial.setName("Aplikacja notarialna");
                notarial.setShortName("Notarialna");
                notarial.setKind(TrackKind.EXAM);
                return args[0].equals(4L) ? Optional.of(notarial) : Optional.empty();
            });

            UserPoolActRepository poolActs = FakeRepos.proxy(UserPoolActRepository.class, (method, args) -> {
                assertThat(method).isEqualTo("findActIdsByUserId");
                assertThat(args[0]).isEqualTo(1L);
                return poolActIds;
            });

            ExamAttemptRepository exams = FakeRepos.proxy(ExamAttemptRepository.class, (method, args) -> {
                assertThat(method).isEqualTo("findAllForUser");
                assertThat(args[0]).isEqualTo(1L);
                ExamAttempt attempt = new ExamAttempt();
                attempt.setFinishedAt(T.minusDays(1));
                attempt.setScopeLabel("Prawo karne");
                attempt.setQuestionCount(150);
                attempt.setAnsweredCount(140);
                attempt.setCorrectCount(113);
                attempt.setPassThresholdPercent(75);
                attempt.setPassed(true);
                attempt.setTimeLimitMinutes(150);
                attempt.setDurationSeconds(7_000);
                attempt.setTimedOut(false);
                return List.of(attempt);
            });

            CustomTestSessionService customTests = org.mockito.Mockito.mock(CustomTestSessionService.class);
            org.mockito.Mockito.when(customTests.listAll(1L)).thenReturn(List.of(new CustomTestSessionSummaryResponse(
                    7L, T.minusDays(2), T.minusDays(2).plusMinutes(20), true, "Prawo karne", CustomTestKind.NEW,
                    List.of(2L), List.of(), List.of("OWN_SHEETS", "AUTHORED"),
                    20, 20, 20, 15, 2, 300.0)));

            service = new AccountService(
                    new CurrentUserResolver(FakeRepos.users(users)),
                    FakeRepos.users(users),
                    encoder,
                    states,
                    logs,
                    reports,
                    tracks,
                    poolActs,
                    settings,
                    exams,
                    FakeRepos.proxy(FlashcardAdditionRepository.class, (method, args) -> {
                        assertThat(method).isEqualTo("findExportRows");
                        assertThat(args[0]).isEqualTo(1L);
                        return List.of(new FlashcardAdditionRepository.ExportedAddition() {
                            public Long getQuestionId() { return 42L; }
                            public String getQuestionBody() { return "Co to jest dolus?"; }
                            public LocalDateTime getAddedAt() { return T.minusHours(2); }
                        });
                    }),
                    customTests,
                    FakeRepos.proxy(StudyTimeRepository.class, (method, args) -> {
                        assertThat(method).isEqualTo("findAllForUser");
                        assertThat(args[0]).isEqualTo(1L);
                        StudyTimeDaily day = new StudyTimeDaily();
                        day.setUserId(1L);
                        day.setStudyDay(LocalDate.of(2026, 10, 11));
                        day.setSeconds(1800);
                        return List.of(day);
                    }),
                    new MutableClock(Instant.parse("2026-10-12T08:00:00Z"), ZoneId.of("Europe/Warsaw")));
        }
    }

    @Test
    void exportContainsSavedCustomTestsAndDailyStudyTime() {
        Fixture f = new Fixture();

        AccountExportResponse export = f.service.export(f.jan);

        assertThat(export.customTests()).hasSize(1);
        AccountExportResponse.CustomTestItem test = export.customTests().get(0);
        assertThat(test.scope()).isEqualTo("Prawo karne");
        assertThat(test.kind()).isEqualTo("NEW");
        assertThat(test.questionCount()).isEqualTo(20);
        assertThat(test.correctCount()).isEqualTo(15);
        assertThat(test.guessedCount()).isEqualTo(2);
        assertThat(test.finishedAt()).isNotNull();
        assertThat(export.studyTime()).hasSize(1);
        assertThat(export.studyTime().get(0).day()).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(export.studyTime().get(0).seconds()).isEqualTo(1800);
    }

    @Test
    void exportContainsAccountQuestionStatesPerModeAndHistory() {
        Fixture f = new Fixture();

        AccountExportResponse export = f.service.export(f.jan);

        assertThat(export.exportedAt()).isEqualTo(Instant.parse("2026-10-12T08:00:00Z"));
        assertThat(export.account().privateEmail()).isEqualTo("jan@gmail.com");
        assertThat(export.account().universityEmail()).isEqualTo("jan@uni.pl");
        assertThat(export.account().role()).isEqualTo("STUDENT");
        assertThat(export.account().universityEmailVerified()).isTrue();
        assertThat(export.account().termsAcceptedAt()).isEqualTo(T.minusDays(30));

        // to samo pytanie ma osobny harmonogram w teście i w fiszkach
        assertThat(export.questionStates()).hasSize(2);
        AccountExportResponse.QuestionStateItem flashcard = export.questionStates().get(0);
        assertThat(flashcard.questionId()).isEqualTo(10L);
        assertThat(flashcard.mode()).isEqualTo("FLASHCARD");
        assertThat(flashcard.question()).isEqualTo("Co to jest dolus?");
        assertThat(flashcard.state()).isEqualTo("REVIEW");
        assertThat(flashcard.reps()).isEqualTo(4);
        // brak zapisanej wartości liczymy jako zero, a nie wyjątek
        assertThat(flashcard.lapses()).isEqualTo(0);
        assertThat(flashcard.difficulty()).isEqualTo(0.0);
        assertThat(flashcard.suspended()).isTrue();
        AccountExportResponse.QuestionStateItem test = export.questionStates().get(1);
        assertThat(test.mode()).isEqualTo("TEST");
        assertThat(test.state()).isEqualTo("LEARNING");
        assertThat(test.difficulty()).isEqualTo(5.5);
        assertThat(test.suspended()).isFalse();

        assertThat(export.flashcardAdditions()).hasSize(1);
        AccountExportResponse.FlashcardAdditionItem addition = export.flashcardAdditions().get(0);
        assertThat(addition.questionId()).isEqualTo(42L);
        assertThat(addition.question()).isEqualTo("Co to jest dolus?");
        assertThat(addition.addedAt()).isEqualTo(T.minusHours(2));

        assertThat(export.reviewLogs()).hasSize(1);
        AccountExportResponse.ReviewItem review = export.reviewLogs().get(0);
        assertThat(review.questionId()).isEqualTo(10L);
        assertThat(review.mode()).isEqualTo("TEST");
        assertThat(review.correct()).isEqualTo(true);
        assertThat(review.unsure()).isFalse();

        assertThat(export.questionReports()).hasSize(1);
        AccountExportResponse.ReportItem report = export.questionReports().get(0);
        assertThat(report.questionId()).isEqualTo(10L);
        assertThat(report.question()).isEqualTo("Co to jest dolus?");
        assertThat(report.reason()).isEqualTo("OUTDATED");
        assertThat(report.comment()).isEqualTo("Przepis się zmienił");
        assertThat(report.status()).isEqualTo("NEW");

        assertThat(export.examAttempts()).hasSize(1);
        AccountExportResponse.ExamEntry exam = export.examAttempts().get(0);
        assertThat(exam.scope()).isEqualTo("Prawo karne");
        assertThat(exam.correctCount()).isEqualTo(113);
        assertThat(exam.passed()).isTrue();
        assertThat(exam.timeLimitMinutes()).isEqualTo(150);
        // podejście z własnym progiem w procentach, bez progu w punktach
        assertThat(exam.passThresholdPercent()).isEqualTo(75);
        assertThat(exam.passPoints()).isNull();
    }

    @Test
    void exportHasNoLearningSettingsWhenTheUserNeverSetAny() {
        Fixture f = new Fixture();

        assertThat(f.service.export(f.jan).learningSettings()).isNull();
    }

    @Test
    void exportContainsStoredLearningSettings_withNullsMeaningDefaults() {
        Fixture f = new Fixture();
        UserSettings stored = new UserSettings(1L);
        stored.setNewFlashcardsPerDay(15);
        stored.setMaxReviewsPerDay(300);
        stored.setTrackId(4L);
        stored.setTheme(Theme.LIGHT);
        stored.setShuffleOptions(false);
        f.storedSettings = stored;

        AccountExportResponse.LearningSettings settings = f.service.export(f.jan).learningSettings();

        assertThat(settings.newFlashcardsPerDay()).isEqualTo(15);
        assertThat(settings.newTestQuestionsPerDay()).isNull();
        assertThat(settings.maxReviewsPerDay()).isEqualTo(300);
        assertThat(settings.desiredRetention()).isNull();
        // ścieżka idzie do eksportu jako czytelny adres (slug), a nie numer z bazy
        assertThat(settings.track()).isEqualTo("notarialna");
        assertThat(settings.theme()).isEqualTo(Theme.LIGHT);
        assertThat(settings.shuffleOptions()).isFalse();
        assertThat(settings.sourceFilter()).isNull(); // brak filtra = wszystkie grupy źródeł
        assertThat(settings.poolActIds()).isEmpty();  // brak zawężenia puli
    }

    @Test
    void exportHasNoTrackUntilTheUserPicksOne() {
        Fixture f = new Fixture();
        f.storedSettings = new UserSettings(1L);

        assertThat(f.service.export(f.jan).learningSettings().track()).isNull();
    }

    @Test
    void exportContainsTheSourceFilterAndThePoolNarrowing() {
        Fixture f = new Fixture();
        UserSettings stored = new UserSettings(1L);
        stored.setTrackId(4L);
        stored.setIncludeOtherSheets(false);
        stored.setIncludeAuthored(false);
        f.storedSettings = stored;
        f.poolActIds = List.of(3L, 8L);

        AccountExportResponse.LearningSettings settings = f.service.export(f.jan).learningSettings();

        assertThat(settings.sourceFilter()).isEqualTo("OWN_SHEETS");
        assertThat(settings.poolActIds()).containsExactly(3L, 8L);
    }

    @Test
    void exportContainsThePermanentHintAndTimerSettings() {
        Fixture f = new Fixture();
        UserSettings stored = new UserSettings(1L);
        stored.setHintHiddenTest(true);
        stored.setQuestionTimerHidden(true);
        f.storedSettings = stored;

        AccountExportResponse.LearningSettings settings = f.service.export(f.jan).learningSettings();

        assertThat(settings.hintHiddenTest()).isTrue();
        assertThat(settings.hintHiddenFlashcard()).isFalse();
        assertThat(settings.questionTimerHidden()).isTrue();
    }

    @Test
    void exportNeverContainsPasswordOrTokens() {
        for (var component : AccountExportResponse.Account.class.getRecordComponents()) {
            assertThat(component.getName().toLowerCase().contains("password")).isFalse();
            assertThat(component.getName().toLowerCase().contains("token")).isFalse();
        }
    }

    @Test
    void exportRequiresAnExistingLoggedInUser() {
        Fixture f = new Fixture();

        assertThatThrownBy(() -> f.service.export(null)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> f.service.export(FakeRepos.authentication("usuniety@gmail.com")))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void deleteAccountWithWrongPasswordKeepsTheAccount() {
        Fixture f = new Fixture();

        assertThatThrownBy(() -> f.service.deleteAccount(f.jan, "zlehaslo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nieprawidłowe hasło");

        assertThat(f.users).hasSize(1);
    }

    @Test
    void deleteAccountWithRightPasswordRemovesTheUser() {
        Fixture f = new Fixture();

        f.service.deleteAccount(f.jan, "haslo1234");

        assertThat(f.users).isEmpty();
        // Ten sam token po usunięciu konta jest już nieważny.
        assertThatThrownBy(() -> f.service.export(f.jan)).isInstanceOf(UnauthorizedException.class);
    }
}
