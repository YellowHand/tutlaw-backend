package pl.tutlaw.backend.settings;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.tutlaw.backend.settings.dto.OriginFilterRequest;
import pl.tutlaw.backend.settings.dto.OriginFilterResponse;

/** Filtr pochodzenia pytań w Testach (zapisany na koncie). */
@RestController
@RequestMapping("/api/me/origin-filter")
@RequiredArgsConstructor
public class OriginFilterController {

    private final OriginFilterService service;

    @GetMapping
    public OriginFilterResponse get(Authentication authentication) {
        return service.get(authentication);
    }

    @PutMapping
    public OriginFilterResponse update(
            @Valid @RequestBody OriginFilterRequest request, Authentication authentication) {
        return service.update(request, authentication);
    }
}
