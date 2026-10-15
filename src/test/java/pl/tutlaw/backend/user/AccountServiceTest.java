package pl.tutlaw.backend.user;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import pl.tutlaw.backend.common.exception.UnauthorizedException;
import pl.tutlaw.backend.exam.ExamAttempt;
import pl.tutlaw.backend.exam.ExamAttemptRepository;
import pl.tutlaw.backend.flashcard.FlashcardAdditionRepository;
import pl.tutlaw.backend.review.CustomTestKind;
import pl.tutlaw.backend.review.CustomTestSessionService;
import pl.tutlaw.backend.review.dto.CustomTestSessionSummaryResponse;
import pl.tutlaw.backend.studytime.StudyTimeDaily;
import pl.tutlaw.backend.studytime.StudyTimeRepository;
import pl.tutlaw.backend.progress.ChapterProgressRepository;
import pl.tutlaw.backend.report.CardReportReason;
import pl.tutlaw.backend.report.CardReportRepository;
import pl.tutlaw.backend.report.CardReportStatus;
import pl.tutlaw.backend.review.CardLearningState;
import pl.tutlaw.backend.review.ReviewLogRepository;
import pl.tutlaw.backend.review.ReviewMode;
import pl.tutlaw.backend.review.UserCardStateRepository;
import pl.tutlaw.backend.card.QuestionOrigin;
import pl.tutlaw.backend.settings.Theme;
import pl.tutlaw.backend.settings.UserSettings;
import pl.tutlaw.backend.settings.UserSettingsRepository;
import pl.tutlaw.backend.testsupport.FakeRepos;
import pl.tutlaw.backend.testsupport.MutableClock;
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

            ChapterProgressRepository progress = FakeRepos.proxy(ChapterProgressRepository.class, (method, args) -> {
                assertThat(method).isEqualTo("findExportRows");
                assertThat(args[0]).isEqualTo(1L);
                return List.of(new ChapterProgressRepository.ExportedProgress() {
                    public Long getChapterId() { return 5L; }
                    public String getChapterTitle() { return "Rozdział 5"; }
                    public Boolean getReadFlag() { return true; }
                    public LocalDateTime getReadAt() { return T.minusDays(2); }
                }, new ChapterProgressRepository.ExportedProgress() {
                    public Long getChapterId() { return 6L; }
                    public String getChapterTitle() { return "Rozdział 6"; }
                    public Boolean getReadFlag() { return null; }
                    public LocalDateTime getReadAt() { return null; }
                });
            });

            UserCardStateRepository states = FakeRepos.proxy(UserCardStateRepository.class, (method, args) -> {
                assertThat(method).isEqualTo("findExportRows");
                return List.of(new UserCardStateRepository.ExportedCardState() {
                    public Long getCardId() { return 10L; }
                    public String getCardFront() { return "Co to jest dolus?"; }
                    public CardLearningState getState() { return CardLearningState.REVIEW; }
                    public Integer getReps() { return 4; }
                    public Integer getLapses() { return null; }
                    public Double getStability() { return 12.5; }
                    public Double getDifficulty() { return null; }
                    public LocalDateTime getDueDate() { return T.plusDays(12); }
                    public LocalDateTime getLastReviewAt() { return T.minusDays(1); }
                    public Boolean getSuspended() { return true; }
                });
            });

            ReviewLogRepository logs = FakeRepos.proxy(ReviewLogRepository.class, (method, args) -> {
                assertThat(method).isEqualTo("findExportRows");
                return List.of(new ReviewLogRepository.ExportedReview() {
                    public Long getCardId() { return 10L; }
                    public LocalDateTime getReviewedAt() { return T.minusDays(1); }
                    public ReviewMode getMode() { return ReviewMode.TEST; }
                    public Integer getRating() { return 3; }
                    public Boolean getWasCorrect() { return true; }
                    public Boolean getWasGuessed() { return null; }
                });
            });

            CardReportRepository reports = FakeRepos.proxy(CardReportRepository.class, (method, args) -> {
                assertThat(method).isEqualTo("findExportRows");
                assertThat(args[0]).isEqualTo(1L);
                return List.of(new CardReportRepository.ExportedReport() {
                    public Long getCardId() { return 10L; }
                    public String getCardFront() { return "Co to jest dolus?"; }
                    public CardReportReason getReason() { return CardReportReason.OUTDATED; }
                    public String getComment() { return "Przepis się zmienił"; }
                    public CardReportStatus getStatus() { return CardReportStatus.NEW; }
                    public LocalDateTime getCreatedAt() { return T.minusHours(3); }
                });
            });

            UserSettingsRepository settings = FakeRepos.proxy(UserSettingsRepository.class, (method, args) -> {
                assertThat(method).isEqualTo("findById");
                assertThat(args[0]).isEqualTo(1L);
                return Optional.ofNullable(storedSettings);
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
                    List.of(2L), List.of(), java.util.EnumSet.allOf(QuestionOrigin.class),
                    20, 20, 20, 15, 2, 300.0)));

            service = new AccountService(
                    new CurrentUserResolver(FakeRepos.users(users)),
                    FakeRepos.users(users),
                    encoder,
                    progress,
                    states,
                    logs,
                    reports,
                    settings,
                    exams,
                    FakeRepos.proxy(FlashcardAdditionRepository.class, (method, args) -> {
                        assertThat(method).isEqualTo("findExportRows");
                        assertThat(args[0]).isEqualTo(1L);
                        return List.of(new FlashcardAdditionRepository.ExportedAddition() {
                            public Long getQuestionId() { return 42L; }
                            public String getQuestionFront() { return "Co to jest dolus?"; }
                            public LocalDateTime getAddedAt() { return T.minusHours(2); }
                        });
                    }),
                    customTests,
                    FakeRepos.proxy(StudyTimeRepository.class, (method, args) -> {
                        assertThat(method).isEqualTo("findAllForUser");
                        assertThat(args[0]).isEqualTo(1L);
                        StudyTimeDaily day = new StudyTimeDaily();
                        day.setUserId(1L);
                        day.setStudyDay(java.time.LocalDate.of(2026, 10, 11));
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
        assertThat(export.studyTime().get(0).day()).isEqualTo(java.time.LocalDate.of(2026, 10, 11));
        assertThat(export.studyTime().get(0).seconds()).isEqualTo(1800);
    }

    @Test
    void exportContainsAccountProgressCardStatesAndHistory() {
        Fixture f = new Fixture();

        AccountExportResponse export = f.service.export(f.jan);

        assertThat(export.exportedAt()).isEqualTo(Instant.parse("2026-10-12T08:00:00Z"));
        assertThat(export.account().privateEmail()).isEqualTo("jan@gmail.com");
        assertThat(export.account().universityEmail()).isEqualTo("jan@uni.pl");
        assertThat(export.account().role()).isEqualTo("STUDENT");
        assertThat(export.account().universityEmailVerified()).isTrue();
        assertThat(export.account().termsAcceptedAt()).isEqualTo(T.minusDays(30));

        assertThat(export.chapterProgress()).hasSize(2);
        assertThat(export.chapterProgress().get(0).chapterTitle()).isEqualTo("Rozdział 5");
        assertThat(export.chapterProgress().get(0).read()).isTrue();
        // Brak zapisanej wartości liczymy jako "nie przeczytany", a nie wyjątek.
        assertThat(export.chapterProgress().get(1).read()).isFalse();

        assertThat(export.cardStates()).hasSize(1);
        AccountExportResponse.CardStateItem state = export.cardStates().get(0);
        assertThat(state.question()).isEqualTo("Co to jest dolus?");
        assertThat(state.state()).isEqualTo("REVIEW");
        assertThat(state.reps()).isEqualTo(4);
        assertThat(state.lapses()).isEqualTo(0);
        assertThat(state.difficulty()).isEqualTo(0.0);
        assertThat(state.suspended()).isTrue();

        assertThat(export.flashcardAdditions()).hasSize(1);
        AccountExportResponse.FlashcardAdditionItem addition = export.flashcardAdditions().get(0);
        assertThat(addition.questionId()).isEqualTo(42L);
        assertThat(addition.question()).isEqualTo("Co to jest dolus?");
        assertThat(addition.addedAt()).isEqualTo(T.minusHours(2));

        assertThat(export.reviewLogs()).hasSize(1);
        AccountExportResponse.ReviewItem review = export.reviewLogs().get(0);
        assertThat(review.mode()).isEqualTo("TEST");
        assertThat(review.correct()).isEqualTo(true);
        assertThat(review.unsure()).isFalse();

        assertThat(export.cardReports()).hasSize(1);
        AccountExportResponse.ReportItem report = export.cardReports().get(0);
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
        // podejście sprzed stałego formatu egzaminu: własny próg w procentach, bez progu w punktach
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
        stored.setNewCardsPerDay(15);
        stored.setMaxReviewsPerDay(300);
        stored.setExamOrigin(QuestionOrigin.NOTARY);
        stored.setTheme(Theme.LIGHT);
        stored.setShuffleOptions(false);
        f.storedSettings = stored;

        AccountExportResponse.LearningSettings settings = f.service.export(f.jan).learningSettings();

        assertThat(settings.newCardsPerDay()).isEqualTo(15);
        assertThat(settings.newTestQuestionsPerDay()).isNull();
        assertThat(settings.maxReviewsPerDay()).isEqualTo(300);
        assertThat(settings.desiredRetention()).isNull();
        assertThat(settings.examOrigin()).isEqualTo(QuestionOrigin.NOTARY);
        assertThat(settings.theme()).isEqualTo(Theme.LIGHT);
        assertThat(settings.shuffleOptions()).isFalse();
        assertThat(settings.testOrigins()).isNull(); // brak filtra = wszystkie pochodzenia
    }

    @Test
    void exportContainsTheOriginFilter() {
        Fixture f = new Fixture();
        UserSettings stored = new UserSettings(1L);
        stored.setTestOrigins("NOTARY,BAILIFF");
        f.storedSettings = stored;

        assertThat(f.service.export(f.jan).learningSettings().testOrigins()).isEqualTo("NOTARY,BAILIFF");
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
