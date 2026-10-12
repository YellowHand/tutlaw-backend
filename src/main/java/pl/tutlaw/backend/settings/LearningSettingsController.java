package pl.tutlaw.backend.settings;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.tutlaw.backend.settings.dto.LearningSettingsResponse;
import pl.tutlaw.backend.settings.dto.UpdateLearningSettingsRequest;

@RestController
@RequestMapping("/api/me/settings")
@RequiredArgsConstructor
public class LearningSettingsController {

    private final LearningSettingsService service;

    @GetMapping
    public LearningSettingsResponse get(Authentication authentication) {
        return service.get(authentication);
    }

    @PutMapping
    public LearningSettingsResponse update(
            @Valid @RequestBody UpdateLearningSettingsRequest request, Authentication authentication) {
        return service.update(request, authentication);
    }
}
