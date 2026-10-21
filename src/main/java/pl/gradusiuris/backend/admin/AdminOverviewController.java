package pl.gradusiuris.backend.admin;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pl.gradusiuris.backend.admin.dto.AdminOverviewResponse;

/** Ekran startowy panelu administratora. Tylko administrator (zabezpiecza też {@code /api/admin/**}). */
@RestController
@RequestMapping("/api/admin/overview")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminOverviewController {

    private final AdminOverviewService service;

    /** Liczby: aplikacje, przedmioty, akty (i ukryte), arkusze (i ukryte), pytania (i ukryte), zgłoszenia do rozpatrzenia. */
    @GetMapping
    public AdminOverviewResponse overview() {
        return service.overview();
    }
}
