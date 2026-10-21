package pl.gradusiuris.backend.report;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pl.gradusiuris.backend.report.dto.QuestionReportRequest;
import pl.gradusiuris.backend.report.dto.QuestionReportResponse;

@RestController
@RequiredArgsConstructor
public class QuestionReportController {

    private final QuestionReportService reportService;

    /** Zgłoszenie błędu w pytaniu (zalogowany użytkownik, nieukryte pytanie). */
    @PostMapping("/api/questions/{questionId}/reports")
    public QuestionReportResponse report(
            @PathVariable Long questionId,
            @Valid @RequestBody QuestionReportRequest request,
            Authentication authentication) {
        return reportService.report(questionId, request, authentication);
    }
}
