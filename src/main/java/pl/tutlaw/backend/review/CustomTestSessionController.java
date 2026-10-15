package pl.tutlaw.backend.review;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.tutlaw.backend.review.dto.CustomTestSessionDetailResponse;
import pl.tutlaw.backend.review.dto.CustomTestSessionSummaryResponse;

import java.util.List;

/** "Moje testy": zapisane własne testy użytkownika (lista i szczegóły z odpowiedziami). */
@RestController
@RequestMapping("/api/custom-tests/sessions")
@RequiredArgsConstructor
public class CustomTestSessionController {

    private final CustomTestSessionService service;

    /** Ostatnie 50 testów, od najnowszego. Nieukończone mają {@code finished = false}. */
    @GetMapping
    public List<CustomTestSessionSummaryResponse> list(Authentication authentication) {
        return service.list(authentication);
    }

    /** Test z wszystkimi pytaniami i odpowiedziami użytkownika; cudzy albo nieistniejący to 404. */
    @GetMapping("/{id}")
    public CustomTestSessionDetailResponse detail(@PathVariable Long id, Authentication authentication) {
        return service.detail(id, authentication);
    }
}
