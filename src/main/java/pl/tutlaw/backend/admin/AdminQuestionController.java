package pl.tutlaw.backend.admin;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pl.tutlaw.backend.admin.dto.AdminQuestionRequest;
import pl.tutlaw.backend.admin.dto.AdminQuestionResponse;
import pl.tutlaw.backend.admin.dto.AdminSourceResponse;
import pl.tutlaw.backend.common.PageParams;
import pl.tutlaw.backend.common.PageResponse;
import pl.tutlaw.backend.mark.MarkStatus;
import pl.tutlaw.backend.mark.QuestionMarkType;

import java.util.List;

/**
 * Pytania w panelu administratora: lista z wyszukiwarką i filtrami, podgląd, dodawanie i edycja. Ukrywanie pytania
 * i jego fiszki to istniejące {@code PUT /api/questions/{id}/hidden} i {@code /flashcard-hidden}. Pytań się nie usuwa.
 * Tylko administrator.
 */
@RestController
@RequestMapping("/api/admin/questions")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminQuestionController {

    private final AdminQuestionService service;

    /**
     * Pytania (także ukryte) od najnowszego. {@code query} szuka w treści, odpowiedziach i podstawie prawnej albo po
     * numerze pytania; {@code withoutSection} to pytania bez rozdziału; {@code reported} to pytania z otwartym zgłoszeniem;
     * {@code mark} to pytania z oznaczeniem w stanie {@code markStatus} (domyślnie zatwierdzonym).
     */
    @GetMapping
    public PageResponse<AdminQuestionResponse> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long actId,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) Long sourceId,
            @RequestParam(required = false) Boolean hidden,
            @RequestParam(required = false) Boolean withoutSection,
            @RequestParam(required = false) Boolean reported,
            @RequestParam(required = false) QuestionMarkType mark,
            @RequestParam(required = false) MarkStatus markStatus,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        AdminQuestionFilter filter = new AdminQuestionFilter(query, actId, sectionId, sourceId, hidden,
                Boolean.TRUE.equals(withoutSection), Boolean.TRUE.equals(reported), mark, markStatus);
        return service.list(filter, PageParams.of(page, size));
    }

    /** Źródła pytań (pytania autorskie i wszystkie arkusze, także ukryte) do list wyboru w formularzu i filtrach. */
    @GetMapping("/sources")
    public List<AdminSourceResponse> sources() {
        return service.sources();
    }

    @GetMapping("/{id}")
    public AdminQuestionResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminQuestionResponse create(@Valid @RequestBody AdminQuestionRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public AdminQuestionResponse update(@PathVariable Long id, @Valid @RequestBody AdminQuestionRequest request) {
        return service.update(id, request);
    }
}
