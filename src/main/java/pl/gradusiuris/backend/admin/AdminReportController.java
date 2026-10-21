package pl.gradusiuris.backend.admin;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pl.gradusiuris.backend.admin.dto.AdminReportResponse;
import pl.gradusiuris.backend.admin.dto.AdminReportSummaryResponse;
import pl.gradusiuris.backend.admin.dto.ReportDecisionRequest;
import pl.gradusiuris.backend.admin.dto.ReportsUpdatedResponse;
import pl.gradusiuris.backend.common.PageParams;
import pl.gradusiuris.backend.common.PageResponse;
import pl.gradusiuris.backend.report.QuestionReportReason;
import pl.gradusiuris.backend.report.QuestionReportStatus;

/** Zgłoszenia błędów w pytaniach: lista, podsumowanie i rozpatrywanie. Tylko administrator. */
@RestController
@RequestMapping("/api/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminReportController {

    private final AdminReportService service;

    /** Zgłoszenia w danym stanie ({@code status}, domyślnie NEW), opcjonalnie z jednym powodem, od najnowszego. */
    @GetMapping
    public PageResponse<AdminReportResponse> list(
            @RequestParam(required = false) QuestionReportStatus status,
            @RequestParam(required = false) QuestionReportReason reason,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return service.list(status, reason, PageParams.of(page, size));
    }

    @GetMapping("/summary")
    public AdminReportSummaryResponse summary() {
        return service.summary();
    }

    /** Rozpatruje jedno zgłoszenie ({@code RESOLVED}, {@code REJECTED}) albo otwiera je ponownie ({@code NEW}). */
    @PutMapping("/{id}")
    public AdminReportResponse decide(@PathVariable Long id, @Valid @RequestBody ReportDecisionRequest request) {
        return service.decide(id, request.status());
    }

    /** Rozpatruje wszystkie otwarte zgłoszenia jednego pytania naraz (zwykle po jego poprawieniu). */
    @PutMapping("/questions/{questionId}")
    public ReportsUpdatedResponse decideForQuestion(
            @PathVariable Long questionId, @Valid @RequestBody ReportDecisionRequest request) {
        return service.decideForQuestion(questionId, request.status());
    }
}
