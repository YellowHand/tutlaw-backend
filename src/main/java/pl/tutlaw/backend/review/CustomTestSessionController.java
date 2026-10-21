package pl.tutlaw.backend.review;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.tutlaw.backend.common.PageParams;
import pl.tutlaw.backend.common.PageResponse;
import pl.tutlaw.backend.review.dto.CustomTestSessionDetailResponse;
import pl.tutlaw.backend.review.dto.CustomTestSessionSummaryResponse;

/** "Moje testy": zapisane własne testy użytkownika (lista i szczegóły z odpowiedziami). */
@RestController
@RequestMapping("/api/custom-tests/sessions")
@RequiredArgsConstructor
public class CustomTestSessionController {

    private final CustomTestSessionService service;

    /**
     * Testy użytkownika, od najnowszego, stronami ({@code page} od 0, {@code size} domyślnie 20, najwyżej 100).
     * Nieukończone mają {@code finished = false}. Odpowiedź to {@code PageResponse}: {@code items}, {@code page},
     * {@code size}, {@code totalElements}, {@code totalPages}.
     */
    @GetMapping
    public PageResponse<CustomTestSessionSummaryResponse> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            Authentication authentication) {
        return service.list(PageParams.ofList(page, size), authentication);
    }

    /** Test z wszystkimi pytaniami i odpowiedziami użytkownika; cudzy albo nieistniejący to 404. */
    @GetMapping("/{id}")
    public CustomTestSessionDetailResponse detail(@PathVariable Long id, Authentication authentication) {
        return service.detail(id, authentication);
    }
}
