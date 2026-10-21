package pl.tutlaw.backend.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tutlaw.backend.admin.dto.AdminReportResponse;
import pl.tutlaw.backend.admin.dto.AdminReportSummaryResponse;
import pl.tutlaw.backend.admin.dto.ReportsUpdatedResponse;
import pl.tutlaw.backend.common.GroupCount;
import pl.tutlaw.backend.common.PageParams;
import pl.tutlaw.backend.common.PageResponse;
import pl.tutlaw.backend.common.exception.ConflictException;
import pl.tutlaw.backend.common.exception.ResourceNotFoundException;
import pl.tutlaw.backend.question.Question;
import pl.tutlaw.backend.question.QuestionRepository;
import pl.tutlaw.backend.report.QuestionReport;
import pl.tutlaw.backend.report.QuestionReportReason;
import pl.tutlaw.backend.report.QuestionReportRepository;
import pl.tutlaw.backend.report.QuestionReportStatus;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Zgłoszenia błędów w pytaniach w panelu administratora: lista z filtrem stanu i powodu, podsumowanie, rozpatrzenie
 * jednego zgłoszenia albo wszystkich otwartych zgłoszeń jednego pytania (po poprawieniu pytania). Rozpatrzenie zapisuje
 * chwilę (resolvedAt); ponowne otwarcie ją czyści.
 */
@Service
@RequiredArgsConstructor
public class AdminReportService {

    private final QuestionReportRepository reportRepository;
    private final QuestionRepository questionRepository;
    private final Clock clock;

    /** Zgłoszenia w danym stanie (domyślnie nowe), od najnowszego; {@code reason} zawęża do jednego powodu. */
    @Transactional(readOnly = true)
    public PageResponse<AdminReportResponse> list(QuestionReportStatus status, QuestionReportReason reason, PageParams params) {
        Page<QuestionReport> page = reportRepository.findForAdmin(
                status == null ? QuestionReportStatus.NEW : status,
                reason == null,
                reason == null ? QuestionReportReason.OTHER : reason,
                PageRequest.of(params.page(), params.size()));
        Map<Long, Long> open = new HashMap<>();
        List<Long> questionIds = page.getContent().stream().map(r -> r.getQuestion().getId()).distinct().toList();
        if (!questionIds.isEmpty()) {
            for (GroupCount row : reportRepository.countOpenByQuestion(questionIds)) {
                open.put(row.getGroupId(), row.getTotal());
            }
        }
        List<AdminReportResponse> items = new ArrayList<>();
        for (QuestionReport report : page.getContent()) {
            items.add(toResponse(report, open.getOrDefault(report.getQuestion().getId(), 0L)));
        }
        return new PageResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AdminReportSummaryResponse summary() {
        return new AdminReportSummaryResponse(
                reportRepository.countByStatus(QuestionReportStatus.NEW),
                reportRepository.countByStatus(QuestionReportStatus.RESOLVED),
                reportRepository.countByStatus(QuestionReportStatus.REJECTED));
    }

    /**
     * Zmienia stan jednego zgłoszenia. Ponowne otwarcie (NEW) jest możliwe, dopóki ten sam użytkownik nie ma już
     * otwartego zgłoszenia tego pytania (baza dopuszcza tylko jedno).
     */
    @Transactional
    public AdminReportResponse decide(Long id, QuestionReportStatus status) {
        QuestionReport report = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Nie ma takiego zgłoszenia."));
        if (status == QuestionReportStatus.NEW) {
            boolean clash = report.getStatus() != QuestionReportStatus.NEW
                    && reportRepository.findByUserIdAndQuestionIdAndStatus(
                            report.getUser().getId(), report.getQuestion().getId(), QuestionReportStatus.NEW).isPresent();
            if (clash) {
                throw new ConflictException("Ten użytkownik ma już otwarte zgłoszenie tego pytania.");
            }
            report.setResolvedAt(null);
        } else {
            report.setResolvedAt(report.getStatus() == status && report.getResolvedAt() != null
                    ? report.getResolvedAt() : LocalDateTime.now(clock));
        }
        report.setStatus(status);
        reportRepository.save(report);
        long open = reportRepository.countOpenByQuestion(List.of(report.getQuestion().getId())).stream()
                .mapToLong(GroupCount::getTotal).sum();
        return toResponse(report, open);
    }

    /** Rozpatruje wszystkie otwarte zgłoszenia jednego pytania naraz (RESOLVED albo REJECTED). */
    @Transactional
    public ReportsUpdatedResponse decideForQuestion(Long questionId, QuestionReportStatus status) {
        if (status != QuestionReportStatus.RESOLVED && status != QuestionReportStatus.REJECTED) {
            throw new IllegalArgumentException("Zgłoszenia pytania można oznaczyć jako poprawione (RESOLVED) albo odrzucone (REJECTED).");
        }
        if (!questionRepository.existsById(questionId)) {
            throw new ResourceNotFoundException("Nie ma takiego pytania.");
        }
        return new ReportsUpdatedResponse(reportRepository.resolveOpenForQuestion(questionId, status, LocalDateTime.now(clock)));
    }

    private static AdminReportResponse toResponse(QuestionReport report, long openForQuestion) {
        Question q = report.getQuestion();
        return new AdminReportResponse(
                report.getId(), q.getId(), q.getBody(), q.getOptionA(), q.getOptionB(), q.getOptionC(), q.getCorrectOption(),
                q.isHidden(), report.getReason(), report.getComment(), report.getStatus(), report.getCreatedAt(),
                report.getResolvedAt(), openForQuestion);
    }
}
