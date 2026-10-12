package pl.tutlaw.backend.review;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.tutlaw.backend.review.dto.DifficultCardsResponse;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class SuspensionController {

    private final SuspensionService suspensionService;

    /** Trudne (nadal w nauce) i zawieszone pytania zalogowanego użytkownika. */
    @GetMapping("/difficult-cards")
    public DifficultCardsResponse list(Authentication authentication) {
        return suspensionService.list(authentication);
    }

    @PutMapping("/suspended-cards/{cardId}")
    public ResponseEntity<Void> suspend(@PathVariable Long cardId, Authentication authentication) {
        suspensionService.suspend(cardId, authentication);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/suspended-cards/{cardId}")
    public ResponseEntity<Void> unsuspend(@PathVariable Long cardId, Authentication authentication) {
        suspensionService.unsuspend(cardId, authentication);
        return ResponseEntity.noContent().build();
    }
}
