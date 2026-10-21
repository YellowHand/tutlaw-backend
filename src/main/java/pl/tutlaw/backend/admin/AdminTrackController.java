package pl.tutlaw.backend.admin;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pl.tutlaw.backend.admin.dto.ActIdsRequest;
import pl.tutlaw.backend.admin.dto.AdminTrackRequest;
import pl.tutlaw.backend.admin.dto.AdminTrackResponse;

import java.util.List;

/**
 * Aplikacje (pięć istniejących, nowe można dodać) i ścieżka Student. Tylko administrator. Termin egzaminu aplikacji
 * ustawia się istniejącym {@code PUT /api/exam-terms/{trackId}}.
 */
@RestController
@RequestMapping("/api/admin/tracks")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminTrackController {

    private final AdminTrackService service;

    /** Wszystkie ścieżki z listą aktów, liczbą arkuszy, liczbą użytkowników, którzy je wybrali, i terminem egzaminu. */
    @GetMapping
    public List<AdminTrackResponse> list() {
        return service.list();
    }

    /** Nowa aplikacja (z egzaminem). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminTrackResponse create(@Valid @RequestBody AdminTrackRequest request) {
        return service.create(request);
    }

    /** Zmiana nazwy, krótkiej nazwy i miejsca na liście. Adres (slug) się nie zmienia. */
    @PutMapping("/{id}")
    public AdminTrackResponse update(@PathVariable Long id, @Valid @RequestBody AdminTrackRequest request) {
        return service.update(id, request);
    }

    /** Usuwa pustą aplikację (bez arkuszy, której nikt nie wybrał). Inaczej 409. Ścieżki Student nie da się usunąć (400). */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    /** Zastępuje listę aktów aplikacji (nie dotyczy ścieżki Student: jej akty biorą się z przedmiotów). */
    @PutMapping("/{id}/acts")
    public AdminTrackResponse setActs(@PathVariable Long id, @Valid @RequestBody ActIdsRequest request) {
        return service.setActs(id, request.actIds());
    }
}
