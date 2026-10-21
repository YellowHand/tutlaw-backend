package pl.gradusiuris.backend.mark;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.gradusiuris.backend.common.PageParams;
import pl.gradusiuris.backend.common.PageResponse;
import pl.gradusiuris.backend.mark.dto.BulkMarkRequest;
import pl.gradusiuris.backend.mark.dto.BulkMarkResponse;
import pl.gradusiuris.backend.mark.dto.MarkAnalysisResponse;
import pl.gradusiuris.backend.mark.dto.MarkDecisionRequest;
import pl.gradusiuris.backend.mark.dto.MarkSummaryResponse;
import pl.gradusiuris.backend.mark.dto.MarkedQuestionResponse;
import pl.gradusiuris.backend.mark.dto.QuestionMarkResponse;

/**
 * Oznaczenia pytań. Tylko administrator (i tak chroni go też {@code /api/admin/**} w konfiguracji zabezpieczeń).
 * Użytkownicy zwykli nigdy nie widzą oznaczeń, tylko skutek: testy „Pytania z terminów" i „Pułapki".
 */
@RestController
@RequestMapping("/api/admin/question-marks")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class QuestionMarkController {

    private final QuestionMarkService markService;

    /** Ile jest oznaczeń każdego rodzaju w każdym stanie (propozycje, zatwierdzone, odrzucone). */
    @GetMapping("/summary")
    public MarkSummaryResponse summary() {
        return markService.summary();
    }

    /**
     * Uruchamia automat na wszystkich pytaniach: dodaje propozycje (stan SUGGESTED), niczego nie zatwierdza i nie
     * nadpisuje wcześniejszych decyzji. Można uruchamiać wielokrotnie (np. po wczytaniu nowych pytań).
     */
    @PostMapping("/analyze")
    public MarkAnalysisResponse analyze() {
        return markService.analyze();
    }

    /**
     * Oznaczenia w danym stanie z treścią pytań, stronami. {@code status} domyślnie SUGGESTED (do przejrzenia),
     * {@code mark} opcjonalnie zawęża do jednego rodzaju.
     */
    @GetMapping
    public PageResponse<MarkedQuestionResponse> list(
            @RequestParam(required = false) MarkStatus status,
            @RequestParam(required = false) QuestionMarkType mark,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return markService.list(status, mark, PageParams.of(page, size));
    }

    /**
     * Hurtowa zmiana stanu: np. {@code {"from":"SUGGESTED","to":"CONFIRMED"}} zatwierdza wszystkie propozycje,
     * a {@code "marks":["TRAP_NEGATION"]} zawęża do wybranych rodzajów.
     */
    @PostMapping("/bulk")
    public BulkMarkResponse bulk(@Valid @RequestBody BulkMarkRequest request, Authentication authentication) {
        return markService.bulk(request, authentication);
    }

    /**
     * Decyzja o jednym oznaczeniu jednego pytania: {@code {"status":"CONFIRMED"}} nadaje je ręcznie (albo zatwierdza
     * propozycję), {@code {"status":"REJECTED"}} cofa je (i automat nie zaproponuje go ponownie).
     */
    @PutMapping("/questions/{questionId}/{mark}")
    public QuestionMarkResponse decide(
            @PathVariable Long questionId,
            @PathVariable QuestionMarkType mark,
            @Valid @RequestBody MarkDecisionRequest request,
            Authentication authentication) {
        return markService.decide(questionId, mark, request.status(), authentication);
    }
}
