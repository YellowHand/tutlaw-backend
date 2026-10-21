package pl.tutlaw.backend.admin;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pl.tutlaw.backend.admin.dto.ActAssignmentsRequest;
import pl.tutlaw.backend.admin.dto.AdminActOptionResponse;
import pl.tutlaw.backend.admin.dto.AdminActRequest;
import pl.tutlaw.backend.admin.dto.AdminActResponse;
import pl.tutlaw.backend.admin.dto.AdminFieldResponse;
import pl.tutlaw.backend.admin.dto.HiddenRequest;
import pl.tutlaw.backend.common.PageParams;
import pl.tutlaw.backend.common.PageResponse;
import pl.tutlaw.backend.legal.LegalActKind;

import java.util.List;

/** Akty prawne i zagadnienia: lista, dodawanie, zmiana, ukrywanie, usuwanie i przypisywanie. Tylko administrator. */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminActController {

    private final AdminActService service;

    /** Dziedziny prawa (tylko do odczytu), do wyboru przy dodawaniu aktu. */
    @GetMapping("/fields")
    public List<AdminFieldResponse> fields() {
        return service.fields();
    }

    /**
     * Akty stronami (domyślnie 50, najwyżej 200): {@code query} szuka w nazwie, skrócie i adresie (bez względu na wielkość
     * liter i polskie znaki), reszta filtrów zawęża listę; {@code unassigned=true} pokazuje akty bez żadnego przypisania.
     */
    @GetMapping("/acts")
    public PageResponse<AdminActResponse> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long fieldId,
            @RequestParam(required = false) LegalActKind kind,
            @RequestParam(required = false) Boolean hidden,
            @RequestParam(required = false) Long trackId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false, defaultValue = "false") boolean unassigned,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return service.list(
                new AdminActService.ActFilter(query, fieldId, kind, hidden, trackId, courseId, unassigned),
                PageParams.of(page, size));
    }

    /** Wszystkie akty (także ukryte) w lekkiej postaci: do list wyboru przy przypisywaniu. */
    @GetMapping("/acts/options")
    public List<AdminActOptionResponse> options() {
        return service.options();
    }

    @GetMapping("/acts/{id}")
    public AdminActResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/acts")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminActResponse create(@Valid @RequestBody AdminActRequest request) {
        return service.create(request);
    }

    @PutMapping("/acts/{id}")
    public AdminActResponse update(@PathVariable Long id, @Valid @RequestBody AdminActRequest request) {
        return service.update(id, request);
    }

    /** Ukrywa ({@code true}) albo przywraca ({@code false}) akt; ukryty znika wszystkim użytkownikom, pytania zostają. */
    @PutMapping("/acts/{id}/hidden")
    public AdminActResponse setHidden(@PathVariable Long id, @Valid @RequestBody HiddenRequest request) {
        return service.setHidden(id, request.hidden());
    }

    /** Pełne przypisanie aktu do aplikacji i przedmiotów Studenta (zastępuje dotychczasowe). */
    @PutMapping("/acts/{id}/assignments")
    public AdminActResponse setAssignments(@PathVariable Long id, @Valid @RequestBody ActAssignmentsRequest request) {
        return service.setAssignments(id, request.trackIds(), request.courseIds());
    }

    /** Usuwa akt bez pytań (razem z rozdziałami i przypisaniami). Akt z pytaniami: 409, można go tylko ukryć. */
    @DeleteMapping("/acts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
