package pl.gradusiuris.backend.admin;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import pl.gradusiuris.backend.admin.dto.AdminReportResponse;
import pl.gradusiuris.backend.admin.dto.ReportsUpdatedResponse;
import pl.gradusiuris.backend.common.PageParams;
import pl.gradusiuris.backend.common.PageResponse;
import pl.gradusiuris.backend.common.exception.ConflictException;
import pl.gradusiuris.backend.common.exception.ResourceNotFoundException;
import pl.gradusiuris.backend.question.CorrectOption;
import pl.gradusiuris.backend.question.Question;
import pl.gradusiuris.backend.question.QuestionRepository;
import pl.gradusiuris.backend.report.QuestionReport;
import pl.gradusiuris.backend.report.QuestionReportReason;
import pl.gradusiuris.backend.report.QuestionReportRepository;
import pl.gradusiuris.backend.report.QuestionReportStatus;
import pl.gradusiuris.backend.testsupport.FakeRepos;
import pl.gradusiuris.backend.testsupport.InMemory;
import pl.gradusiuris.backend.testsupport.MutableClock;
import pl.gradusiuris.backend.user.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Zgłoszenia błędów w panelu administratora na prawdziwym AdminReportService, z repozytoriami "w pamięci". */
class AdminReportServiceTest {

    private final MutableClock clock = MutableClock.at("2026-10-21T10:00:00Z");
    private final List<QuestionReport> reports = new ArrayList<>();
    private final List<Question> questions = new ArrayList<>();
    private Object[] listArgs;
    private final AdminReportService service;

    AdminReportServiceTest() {
        QuestionReportRepository reportRepository = InMemory.repo(QuestionReportRepository.class, reports, QuestionReport::getId, QuestionReport::setId,
                (method, args) -> switch (method) {
                    case "findForAdmin" -> {
                        listArgs = args;
                        QuestionReportStatus status = (QuestionReportStatus) args[0];
                        yield new PageImpl<>(reports.stream().filter(r -> r.getStatus() == status).toList(), (Pageable) args[3], reports.size());
                    }
                    case "countOpenByQuestion" -> questions.stream()
                            .filter(q -> ((java.util.Collection<?>) args[0]).contains(q.getId()))
                            .filter(q -> open(q.getId()) > 0)
                            .map(q -> InMemory.group(q.getId(), open(q.getId()))).toList();
                    case "countByStatus" -> reports.stream().filter(r -> r.getStatus() == args[0]).count();
                    case "findByUserIdAndQuestionIdAndStatus" -> reports.stream()
                            .filter(r -> r.getUser().getId().equals(args[0]) && r.getQuestion().getId().equals(args[1]) && r.getStatus() == args[2])
                            .findFirst();
                    case "resolveOpenForQuestion" -> {
                        int[] n = {0};
                        reports.stream().filter(r -> r.getQuestion().getId().equals(args[0]) && r.getStatus() == QuestionReportStatus.NEW).forEach(r -> {
                            r.setStatus((QuestionReportStatus) args[1]);
                            r.setResolvedAt((LocalDateTime) args[2]);
                            n[0]++;
                        });
                        yield n[0];
                    }
                    default -> throw new AssertionError("nieoczekiwane: " + method);
                });
        QuestionRepository questionRepository = FakeRepos.proxy(QuestionRepository.class, (method, args) -> {
            if (method.equals("existsById")) {
                return questions.stream().anyMatch(q -> q.getId().equals(args[0]));
            }
            throw new AssertionError("nieoczekiwane: " + method);
        });
        service = new AdminReportService(reportRepository, questionRepository, clock);
        questions.add(question(1L));
        questions.add(question(2L));
    }

    private long open(Long questionId) {
        return reports.stream().filter(r -> r.getQuestion().getId().equals(questionId) && r.getStatus() == QuestionReportStatus.NEW).count();
    }

    private static Question question(Long id) {
        Question q = new Question();
        q.setId(id);
        q.setBody("Pytanie " + id);
        q.setOptionA("a");
        q.setOptionB("b");
        q.setOptionC("c");
        q.setCorrectOption(CorrectOption.B);
        return q;
    }

    private QuestionReport report(Long id, Long userId, Long questionId, QuestionReportStatus status) {
        QuestionReport r = new QuestionReport();
        r.setId(id);
        User user = new User();
        user.setId(userId);
        r.setUser(user);
        r.setQuestion(questions.stream().filter(q -> q.getId().equals(questionId)).findFirst().orElseThrow());
        r.setReason(QuestionReportReason.WRONG_ANSWER);
        r.setComment("Błędna odpowiedź");
        r.setStatus(status);
        r.setCreatedAt(LocalDateTime.of(2026, 10, 1, 9, 0));
        reports.add(r);
        return r;
    }

    @Test
    void list_defaultsToNewReports_withTheQuestionAndTheNumberOfOpenReportsOfThatQuestion() {
        report(1L, 10L, 1L, QuestionReportStatus.NEW);
        report(2L, 11L, 1L, QuestionReportStatus.NEW);
        report(3L, 12L, 2L, QuestionReportStatus.RESOLVED);

        PageResponse<AdminReportResponse> page = service.list(null, null, PageParams.of(0, 20));

        assertThat(page.items()).extracting(AdminReportResponse::id).containsExactly(1L, 2L);
        assertThat(page.items().get(0).openForQuestion()).isEqualTo(2);
        assertThat(page.items().get(0).questionBody()).isEqualTo("Pytanie 1");
        assertThat(page.items().get(0).correctOption()).isEqualTo(CorrectOption.B);
        assertThat(page.items().get(0).comment()).isEqualTo("Błędna odpowiedź");
        assertThat(listArgs[1]).isEqualTo(true);
    }

    @Test
    void list_passesTheStateAndTheReasonFilters() {
        report(1L, 10L, 1L, QuestionReportStatus.RESOLVED);

        PageResponse<AdminReportResponse> page = service.list(QuestionReportStatus.RESOLVED, QuestionReportReason.OUTDATED, PageParams.of(0, 20));

        assertThat(page.items()).hasSize(1);
        assertThat(listArgs[0]).isEqualTo(QuestionReportStatus.RESOLVED);
        assertThat(listArgs[1]).isEqualTo(false);
        assertThat(listArgs[2]).isEqualTo(QuestionReportReason.OUTDATED);
    }

    @Test
    void summary_countsEveryState() {
        report(1L, 10L, 1L, QuestionReportStatus.NEW);
        report(2L, 11L, 1L, QuestionReportStatus.NEW);
        report(3L, 12L, 2L, QuestionReportStatus.RESOLVED);
        report(4L, 13L, 2L, QuestionReportStatus.REJECTED);

        assertThat(service.summary().open()).isEqualTo(2);
        assertThat(service.summary().resolved()).isEqualTo(1);
        assertThat(service.summary().rejected()).isEqualTo(1);
    }

    @Test
    void decide_resolvesAReportAndRecordsTheMoment() {
        QuestionReport r = report(1L, 10L, 1L, QuestionReportStatus.NEW);

        AdminReportResponse response = service.decide(1L, QuestionReportStatus.RESOLVED);

        assertThat(r.getStatus()).isEqualTo(QuestionReportStatus.RESOLVED);
        assertThat(r.getResolvedAt()).isNotNull();
        assertThat(response.status()).isEqualTo(QuestionReportStatus.RESOLVED);
        assertThat(response.openForQuestion()).isZero();
    }

    @Test
    void decide_changingOneDecisionToAnotherKeepsTheOriginalMoment_andReopeningClearsIt() {
        QuestionReport r = report(1L, 10L, 1L, QuestionReportStatus.NEW);
        service.decide(1L, QuestionReportStatus.RESOLVED);
        LocalDateTime first = r.getResolvedAt();
        clock.advance(java.time.Duration.ofHours(5));

        service.decide(1L, QuestionReportStatus.RESOLVED);
        assertThat(r.getResolvedAt()).isEqualTo(first);

        service.decide(1L, QuestionReportStatus.REJECTED);
        assertThat(r.getResolvedAt()).isAfter(first);

        service.decide(1L, QuestionReportStatus.NEW);
        assertThat(r.getStatus()).isEqualTo(QuestionReportStatus.NEW);
        assertThat(r.getResolvedAt()).isNull();
    }

    @Test
    void decide_cannotReopenAReport_whenTheSameUserAlreadyHasAnOpenOneForThatQuestion() {
        report(1L, 10L, 1L, QuestionReportStatus.RESOLVED);
        report(2L, 10L, 1L, QuestionReportStatus.NEW);

        assertThatThrownBy(() -> service.decide(1L, QuestionReportStatus.NEW)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.decide(99L, QuestionReportStatus.RESOLVED)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void decideForQuestion_resolvesAllOpenReportsOfThatQuestionOnly() {
        QuestionReport a = report(1L, 10L, 1L, QuestionReportStatus.NEW);
        QuestionReport b = report(2L, 11L, 1L, QuestionReportStatus.NEW);
        QuestionReport done = report(3L, 12L, 1L, QuestionReportStatus.REJECTED);
        QuestionReport other = report(4L, 10L, 2L, QuestionReportStatus.NEW);

        ReportsUpdatedResponse response = service.decideForQuestion(1L, QuestionReportStatus.RESOLVED);

        assertThat(response.updated()).isEqualTo(2);
        assertThat(a.getStatus()).isEqualTo(QuestionReportStatus.RESOLVED);
        assertThat(b.getResolvedAt()).isEqualTo(LocalDateTime.now(clock));
        assertThat(done.getStatus()).isEqualTo(QuestionReportStatus.REJECTED);
        assertThat(other.getStatus()).isEqualTo(QuestionReportStatus.NEW);
    }

    @Test
    void decideForQuestion_acceptsOnlyResolvedOrRejected_andAKnownQuestion() {
        assertThatThrownBy(() -> service.decideForQuestion(1L, QuestionReportStatus.NEW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.decideForQuestion(99L, QuestionReportStatus.RESOLVED)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(Optional.of(service.decideForQuestion(2L, QuestionReportStatus.REJECTED).updated())).contains(0);
    }
}
