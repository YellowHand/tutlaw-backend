package pl.tutlaw.backend.user;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.tutlaw.backend.user.dto.ResetStudyProgressRequest;
import pl.tutlaw.backend.user.dto.StudyProgressResponse;

/** Postępy w nauce zalogowanego użytkownika: podgląd tego, co zniknie, i zerowanie. */
@RestController
@RequestMapping("/api/me/study-progress")
@RequiredArgsConstructor
public class StudyProgressController {

    private final StudyProgressService service;

    /** Ile czego zniknie po wyzerowaniu (do okna potwierdzenia). */
    @GetMapping
    public StudyProgressResponse summary(Authentication authentication) {
        return service.summary(authentication);
    }

    /**
     * Zeruje postępy (stany nauki, historię odpowiedzi, własne testy, egzaminy próbne i czas nauki); konto, ścieżka,
     * ustawienia i wybrane przedmioty zostają. Wymaga {@code confirm: "WYZERUJ"}. Odpowiada tym, co skasowano.
     */
    @DeleteMapping
    public StudyProgressResponse reset(
            @Valid @RequestBody ResetStudyProgressRequest request, Authentication authentication) {
        return service.reset(request.confirm(), authentication);
    }
}
