package pl.tutlaw.backend.settings;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.tutlaw.backend.settings.dto.ChooseExamRequest;
import pl.tutlaw.backend.settings.dto.LearningSettingsResponse;

/** Wybór aplikacji, do której użytkownik się przygotowuje (od niej zależy data egzaminu i filtr pytań). */
@RestController
@RequestMapping("/api/me/exam")
@RequiredArgsConstructor
public class MyExamController {

    private final LearningSettingsService service;

    @PutMapping
    public LearningSettingsResponse choose(@Valid @RequestBody ChooseExamRequest request, Authentication authentication) {
        return service.chooseExam(request, authentication);
    }
}
