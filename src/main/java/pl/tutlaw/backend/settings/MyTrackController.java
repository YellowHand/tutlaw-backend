package pl.tutlaw.backend.settings;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.tutlaw.backend.settings.dto.ChooseTrackRequest;
import pl.tutlaw.backend.settings.dto.LearningSettingsResponse;

/** Wybór ścieżki, do której użytkownik się przygotowuje (od niej zależy pula pytań i data egzaminu). */
@RestController
@RequestMapping("/api/me/track")
@RequiredArgsConstructor
public class MyTrackController {

    private final LearningSettingsService service;

    @PutMapping
    public LearningSettingsResponse choose(@Valid @RequestBody ChooseTrackRequest request, Authentication authentication) {
        return service.chooseTrack(request, authentication);
    }
}
