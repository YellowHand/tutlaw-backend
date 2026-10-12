package pl.tutlaw.backend.report;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pl.tutlaw.backend.report.dto.CardReportRequest;
import pl.tutlaw.backend.report.dto.CardReportResponse;

@RestController
@RequiredArgsConstructor
public class CardReportController {

    private final CardReportService reportService;

    /** Zgłoszenie błędu w pytaniu (zalogowany użytkownik, pytanie dostępne dla niego w nauce). */
    @PostMapping("/api/cards/{cardId}/reports")
    public CardReportResponse report(
            @PathVariable Long cardId,
            @Valid @RequestBody CardReportRequest request,
            Authentication authentication) {
        return reportService.report(cardId, request, authentication);
    }
}
