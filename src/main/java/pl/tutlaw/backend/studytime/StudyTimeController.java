package pl.tutlaw.backend.studytime;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Zgłaszanie czasu nauki (porcja sekund doliczana do dzisiejszego dnia). */
@RestController
@RequestMapping("/api/me/study-time")
@RequiredArgsConstructor
public class StudyTimeController {

    private final StudyTimeService service;

    @PostMapping
    public ResponseEntity<Void> add(@Valid @RequestBody StudyTimeRequest request, Authentication authentication) {
        service.add(request.seconds(), authentication);
        return ResponseEntity.noContent().build();
    }
}
