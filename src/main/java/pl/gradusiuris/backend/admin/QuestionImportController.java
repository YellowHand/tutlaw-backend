package pl.gradusiuris.backend.admin;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.gradusiuris.backend.admin.dto.ImportQuestionsRequest;
import pl.gradusiuris.backend.admin.dto.ImportReportResponse;

/** Import pytań z CSV i JSON: podgląd z raportem i zapis. Tylko administrator. */
@RestController
@RequestMapping("/api/admin/import")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class QuestionImportController {

    private final QuestionImportService service;

    /**
     * Sprawdza plik i (gdy {@code dryRun} jest fałszem, a plik nie ma błędów) zapisuje pytania. Domyślnie tylko podgląd,
     * więc pomyłka w wywołaniu niczego nie zapisze. Zawsze odpowiada raportem z oceną każdego wiersza.
     */
    @PostMapping("/questions")
    public ImportReportResponse importQuestions(@Valid @RequestBody ImportQuestionsRequest request) {
        return service.run(request);
    }
}
