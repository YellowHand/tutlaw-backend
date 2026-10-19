package pl.tutlaw.backend.report;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import pl.tutlaw.backend.question.Question;
import pl.tutlaw.backend.question.QuestionRepository;
import pl.tutlaw.backend.common.exception.ResourceNotFoundException;
import pl.tutlaw.backend.common.exception.TooManyRequestsException;
import pl.tutlaw.backend.common.exception.UnauthorizedException;
import pl.tutlaw.backend.report.dto.QuestionReportRequest;
import pl.tutlaw.backend.report.dto.QuestionReportResponse;
import pl.tutlaw.backend.testsupport.FakeRepos;
import pl.tutlaw.backend.testsupport.MutableClock;
import pl.tutlaw.backend.testsupport.QuestionRefs;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Zgłaszanie błędów w pytaniach na prawdziwym QuestionReportService, z repozytoriami "w pamięci". */
class QuestionReportServiceTest {

    private static final class Fixture {
        final MutableClock clock = new MutableClock(Instant.parse("2026-10-12T08:00:00Z"), ZoneId.of("Europe/Warsaw"));
        final List<QuestionReport> reports = new ArrayList<>();
        final Map<Long, Question> questions = new HashMap<>();
        final QuestionReportService service;
        final Authentication jan = FakeRepos.authentication("jan@gmail.com");

        Fixture() {
            List<User> users = new ArrayList<>();
            User user = new User();
            user.setId(1L);
            user.setPrivateEmail("jan@gmail.com");
            users.add(user);

            AtomicLong ids = new AtomicLong(1);
            QuestionReportRepository reportRepository = FakeRepos.proxy(QuestionReportRepository.class, (method, args) -> {
                switch (method) {
                    case "save": {
                        QuestionReport r = (QuestionReport) args[0];
                        if (r.getId() == null) {
                            r.setId(ids.getAndIncrement());
                        }
                        if (!reports.contains(r)) {
                            reports.add(r);
                        }
                        return r;
                    }
                    case "findByUserIdAndQuestionIdAndStatus":
                        return reports.stream()
                                .filter(r -> r.getUser().getId().equals(args[0])
                                        && r.getQuestion().getId().equals(args[1])
                                        && r.getStatus() == args[2])
                                .findFirst();
                    case "countByUserIdAndCreatedAtAfter":
                        return reports.stream()
                                .filter(r -> r.getUser().getId().equals(args[0])
                                        && r.getCreatedAt().isAfter((LocalDateTime) args[1]))
                                .count();
                    default:
                        throw new UnsupportedOperationException("QuestionReportRepository." + method);
                }
            });

            QuestionRepository questionRepository = FakeRepos.proxy(QuestionRepository.class, (method, args) -> {
                switch (method) {
                    case "findById":
                        return Optional.ofNullable(questions.get(args[0]));
                    default:
                        throw new UnsupportedOperationException("QuestionRepository." + method);
                }
            });

            service = new QuestionReportService(
                    new CurrentUserResolver(FakeRepos.users(users)), questionRepository, reportRepository, clock);
        }

        Question question(long id, boolean hidden) {
            Question question = QuestionRefs.question(id);
            question.setBody("Pytanie " + id);
            question.setHidden(hidden);
            questions.put(id, question);
            return question;
        }
    }

    private static QuestionReportRequest request(QuestionReportReason reason, String comment) {
        return new QuestionReportRequest(reason, comment);
    }

    @Test
    void savesANewReportWithTrimmedCommentAndStatusNew() {
        Fixture f = new Fixture();
        f.question(10, false);

        QuestionReportResponse response = f.service.report(10L, request(QuestionReportReason.OUTDATED, "  Zmiana od 1 stycznia  "), f.jan);

        assertThat(response.updated()).isFalse();
        assertThat(f.reports).hasSize(1);
        QuestionReport saved = f.reports.get(0);
        assertThat(saved.getStatus()).isEqualTo(QuestionReportStatus.NEW);
        assertThat(saved.getReason()).isEqualTo(QuestionReportReason.OUTDATED);
        assertThat(saved.getComment()).isEqualTo("Zmiana od 1 stycznia");
        assertThat(saved.getUser().getId()).isEqualTo(1L);
        assertThat(saved.getQuestion().getId()).isEqualTo(10L);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void emptyCommentIsStoredAsNull() {
        Fixture f = new Fixture();
        f.question(10, false);

        f.service.report(10L, request(QuestionReportReason.WRONG_ANSWER, "   "), f.jan);

        assertThat(f.reports.get(0).getComment()).isNull();
    }

    @Test
    void secondReportOfTheSameQuestionUpdatesTheOpenOneInsteadOfAddingAnother() {
        Fixture f = new Fixture();
        f.question(10, false);
        f.service.report(10L, request(QuestionReportReason.UNCLEAR, "literówka"), f.jan);

        QuestionReportResponse second = f.service.report(10L, request(QuestionReportReason.WRONG_ANSWER, "to jednak błąd"), f.jan);

        assertThat(second.updated()).isTrue();
        assertThat(f.reports).hasSize(1);
        assertThat(f.reports.get(0).getReason()).isEqualTo(QuestionReportReason.WRONG_ANSWER);
        assertThat(f.reports.get(0).getComment()).isEqualTo("to jednak błąd");
    }

    @Test
    void afterTheOwnerResolvedAReportTheUserMayReportTheQuestionAgain() {
        Fixture f = new Fixture();
        f.question(10, false);
        f.service.report(10L, request(QuestionReportReason.UNCLEAR, "x"), f.jan);
        f.reports.get(0).setStatus(QuestionReportStatus.RESOLVED);

        QuestionReportResponse again = f.service.report(10L, request(QuestionReportReason.UNCLEAR, "nadal niejasne"), f.jan);

        assertThat(again.updated()).isFalse();
        assertThat(f.reports).hasSize(2);
    }

    @Test
    void hiddenOrMissingQuestionLooksTheSame() {
        Fixture f = new Fixture();
        f.question(10, true);

        assertThatThrownBy(() -> f.service.report(10L, request(QuestionReportReason.WRONG_ANSWER, null), f.jan))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> f.service.report(999L, request(QuestionReportReason.WRONG_ANSWER, null), f.jan))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(f.reports).isEmpty();
    }

    @Test
    void badInputIsRejectedBeforeAnythingIsSaved() {
        Fixture f = new Fixture();
        f.question(10, false);

        assertThatThrownBy(() -> f.service.report(10L, request(null, "x"), f.jan))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> f.service.report(10L, request(QuestionReportReason.OTHER, " "), f.jan))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Opisz");
        assertThatThrownBy(() -> f.service.report(10L, request(QuestionReportReason.OTHER, "a".repeat(1001)), f.jan))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1000");
        assertThat(f.reports).isEmpty();
    }

    @Test
    void requiresALoggedInExistingUser() {
        Fixture f = new Fixture();
        f.question(10, false);

        assertThatThrownBy(() -> f.service.report(10L, request(QuestionReportReason.WRONG_ANSWER, null), null))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> f.service.report(
                10L, request(QuestionReportReason.WRONG_ANSWER, null), FakeRepos.authentication("nikt@gmail.com")))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void dailyLimitStopsFloodingButStillAllowsCompletingAnExistingReport() {
        Fixture f = new Fixture();
        for (int i = 1; i <= ReportRules.DAILY_LIMIT + 1; i++) {
            f.question(i, false);
        }
        for (int i = 1; i <= ReportRules.DAILY_LIMIT; i++) {
            f.service.report((long) i, request(QuestionReportReason.WRONG_ANSWER, null), f.jan);
        }

        long extra = ReportRules.DAILY_LIMIT + 1;
        assertThatThrownBy(() -> f.service.report(extra, request(QuestionReportReason.WRONG_ANSWER, null), f.jan))
                .isInstanceOf(TooManyRequestsException.class);
        // Uzupełnienie już wysłanego zgłoszenia nie jest "nowym" i przechodzi.
        QuestionReportResponse completed = f.service.report(1L, request(QuestionReportReason.WRONG_ANSWER, "dopisek"), f.jan);
        assertThat(completed.updated()).isTrue();

        // Po dobie limit się zwalnia.
        f.clock.advance(Duration.ofHours(25));
        QuestionReportResponse later = f.service.report(extra, request(QuestionReportReason.WRONG_ANSWER, null), f.jan);
        assertThat(later.updated()).isFalse();
    }
}
