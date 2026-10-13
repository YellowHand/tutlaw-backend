package pl.tutlaw.backend.flashcard;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.tutlaw.backend.flashcard.dto.FlashcardStatusResponse;

@RestController
@RequiredArgsConstructor
public class FlashcardController {

    private final FlashcardService flashcardService;

    /** „Dodaj do fiszek": fiszka tego pytania trafia do Fiszek zalogowanego użytkownika. */
    @PostMapping("/api/questions/{questionId}/flashcard")
    public FlashcardStatusResponse add(@PathVariable Long questionId, Authentication authentication) {
        return new FlashcardStatusResponse(flashcardService.add(questionId, authentication));
    }

    /** „Usuń z fiszek": cofnięcie ręcznego dodania. */
    @DeleteMapping("/api/questions/{questionId}/flashcard")
    public FlashcardStatusResponse remove(@PathVariable Long questionId, Authentication authentication) {
        return new FlashcardStatusResponse(flashcardService.remove(questionId, authentication));
    }
}
