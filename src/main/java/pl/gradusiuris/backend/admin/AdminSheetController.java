package pl.gradusiuris.backend.admin;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pl.gradusiuris.backend.admin.dto.AdminSheetCreateRequest;
import pl.gradusiuris.backend.admin.dto.AdminSheetResponse;
import pl.gradusiuris.backend.admin.dto.AdminSheetUpdateRequest;
import pl.gradusiuris.backend.admin.dto.HiddenRequest;

import java.util.List;

/** Arkusze egzaminacyjne (aplikacja i rok): lista, dodawanie, zmiana, ukrywanie, usuwanie. Tylko administrator. */
@RestController
@RequestMapping("/api/admin/sheets")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSheetController {

    private final AdminSheetService service;

    /** Arkusze wszystkich aplikacji (albo jednej: {@code trackId}), także ukryte, od najnowszego roku. */
    @GetMapping
    public List<AdminSheetResponse> list(@RequestParam(required = false) Long trackId) {
        return service.list(trackId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminSheetResponse create(@Valid @RequestBody AdminSheetCreateRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public AdminSheetResponse update(@PathVariable Long id, @Valid @RequestBody AdminSheetUpdateRequest request) {
        return service.update(id, request);
    }

    /** Ukrywa ({@code true}) albo przywraca ({@code false}) arkusz; ukryty znika wszystkim użytkownikom, pytania zostają. */
    @PutMapping("/{id}/hidden")
    public AdminSheetResponse setHidden(@PathVariable Long id, @Valid @RequestBody HiddenRequest request) {
        return service.setHidden(id, request.hidden());
    }

    /** Usuwa arkusz bez pytań. Arkusz z pytaniami: 409, można go tylko ukryć. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
