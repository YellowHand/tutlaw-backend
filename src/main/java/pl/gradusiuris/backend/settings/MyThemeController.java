package pl.gradusiuris.backend.settings;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.gradusiuris.backend.settings.dto.ChooseThemeRequest;
import pl.gradusiuris.backend.settings.dto.LearningSettingsResponse;

/** Wybór wyglądu aplikacji (ciemny albo jasny). Wygląd jest zapisany na koncie, więc działa na każdym urządzeniu. */
@RestController
@RequestMapping("/api/me/theme")
@RequiredArgsConstructor
public class MyThemeController {

    private final LearningSettingsService service;

    @PutMapping
    public LearningSettingsResponse choose(@Valid @RequestBody ChooseThemeRequest request, Authentication authentication) {
        return service.chooseTheme(request, authentication);
    }
}
