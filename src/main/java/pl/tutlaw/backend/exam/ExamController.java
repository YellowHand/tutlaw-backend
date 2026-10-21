package pl.tutlaw.backend.exam;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.tutlaw.backend.common.PageParams;
import pl.tutlaw.backend.common.PageResponse;
import pl.tutlaw.backend.exam.dto.ExamAttemptResponse;
import pl.tutlaw.backend.exam.dto.ExamAvailabilityResponse;
import pl.tutlaw.backend.exam.dto.ExamResultResponse;
import pl.tutlaw.backend.exam.dto.ExamSheetsResponse;
import pl.tutlaw.backend.exam.dto.ExamStartResponse;
import pl.tutlaw.backend.exam.dto.ExamSubmitRequest;
import pl.tutlaw.backend.pool.SourceMode;

/** Egzamin próbny. Wszystko dotyczy tylko zalogowanego użytkownika. */
@RestController
@RequestMapping("/api/exams")
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;

    /**
     * Losuje pytania egzaminu (bez poprawnych odpowiedzi). Format jest stały (150 pytań z całej puli), a wybiera się
     * tylko źródło: {@code source} = ALL (domyślnie), MINISTRY albo AUTHORED, albo konkretny egzamin MS z poprzedniego roku
     * ({@code sheetId} z {@code GET /api/exams/sheets}; wtedy {@code source} jest pomijane, a egzamin to cały arkusz).
     * Żądanie nie ma treści. Niczego nie zapisuje.
     */
    @PostMapping("/start")
    public ExamStartResponse start(
            @RequestParam(required = false) SourceMode source,
            @RequestParam(required = false) Long sheetId,
            Authentication authentication) {
        return examService.start(source, sheetId, authentication);
    }

    /**
     * Egzaminy MS do wyboru: arkusze egzaminów wstępnych z poprzednich lat, pogrupowane według aplikacji, z liczbą
     * pytań każdego. Tylko do odczytu.
     */
    @GetMapping("/sheets")
    public ExamSheetsResponse sheets(Authentication authentication) {
        return examService.sheets(authentication);
    }

    /**
     * Ile pytań można wylosować na egzamin z każdego źródła (bez zawężenia puli do aktów i bez pytań zawieszonych
     * w testach). Tylko do odczytu.
     */
    @GetMapping("/availability")
    public ExamAvailabilityResponse availability(Authentication authentication) {
        return examService.availability(authentication);
    }

    /** Ocenia zakończony egzamin, zapisuje podsumowanie i zwraca raport. */
    @PostMapping("/submit")
    public ExamResultResponse submit(@Valid @RequestBody ExamSubmitRequest request, Authentication authentication) {
        return examService.submit(request, authentication);
    }

    /**
     * Podejścia użytkownika, od najnowszego, stronami ({@code page} od 0, {@code size} domyślnie 20, najwyżej 100).
     * Odpowiedź to {@code PageResponse}: {@code items}, {@code page}, {@code size}, {@code totalElements},
     * {@code totalPages}.
     */
    @GetMapping("/history")
    public PageResponse<ExamAttemptResponse> history(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            Authentication authentication) {
        return examService.history(PageParams.ofList(page, size), authentication);
    }

    /**
     * Raport jednego podejścia z zapisanych odpowiedzi (jak raport tuż po egzaminie). Podejścia sprzed zapisu odpowiedzi
     * ({@code detailsAvailable = false}), cudze i nieistniejące to 404.
     */
    @GetMapping("/attempts/{id}")
    public ExamResultResponse attempt(@PathVariable Long id, Authentication authentication) {
        return examService.attempt(id, authentication);
    }
}
