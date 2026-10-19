package pl.tutlaw.backend.review;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.tutlaw.backend.review.dto.DifficultQuestionsResponse;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class SuspensionController {

    private final SuspensionService suspensionService;

    /** Trudne (nadal w nauce) i zawieszone pytania zalogowanego użytkownika. */
    @GetMapping("/difficult-questions")
    public DifficultQuestionsResponse list(Authentication authentication) {
        return suspensionService.list(authentication);
    }

    /** Zawiesza pytanie w trybie ({@code mode}: TEST albo FLASHCARD): test i fiszka tego pytania zawiesza się osobno. */
    @PutMapping("/suspended-questions/{questionId}")
    public ResponseEntity<Void> suspend(
            @PathVariable Long questionId, @RequestParam ReviewMode mode, Authentication authentication) {
        suspensionService.suspend(questionId, mode, authentication);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/suspended-questions/{questionId}")
    public ResponseEntity<Void> unsuspend(
            @PathVariable Long questionId, @RequestParam ReviewMode mode, Authentication authentication) {
        suspensionService.unsuspend(questionId, mode, authentication);
        return ResponseEntity.noContent().build();
    }
}
