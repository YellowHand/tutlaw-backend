package pl.tutlaw.backend.question;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pl.tutlaw.backend.question.dto.HiddenRequest;
import pl.tutlaw.backend.question.dto.QuestionVisibilityResponse;

@RestController
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;

    /** Ukrycie pytania wszystkim użytkownikom i jego przywrócenie. Tylko administrator. */
    @PutMapping("/api/questions/{id}/hidden")
    @PreAuthorize("hasRole('ADMIN')")
    public QuestionVisibilityResponse setHidden(@PathVariable Long id, @Valid @RequestBody HiddenRequest request) {
        return questionService.setHidden(id, request.hidden());
    }

    /** Ukrycie samej fiszki pytania (pytanie zostaje w testach) i jej przywrócenie. Tylko administrator. */
    @PutMapping("/api/questions/{id}/flashcard-hidden")
    @PreAuthorize("hasRole('ADMIN')")
    public QuestionVisibilityResponse setFlashcardHidden(
            @PathVariable Long id, @Valid @RequestBody HiddenRequest request) {
        return questionService.setFlashcardHidden(id, request.hidden());
    }
}
