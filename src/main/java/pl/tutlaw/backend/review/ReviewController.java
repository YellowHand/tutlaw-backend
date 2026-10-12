package pl.tutlaw.backend.review;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pl.tutlaw.backend.review.dto.*;

import java.util.List;

@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewQueueService queueService;
    private final ReviewService reviewService;
    private final MistakesService mistakesService;
    private final PracticeQueueService practiceQueueService;

    // categoryId, subjectId i sourceId są opcjonalne (od najwęższego: źródło, przedmiot, kategoria).
    // Bez żadnego z nich kolejka obejmuje cały materiał użytkownika.
    @GetMapping("/queue")
    public List<ReviewQueueItemResponse> getQueue(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long sourceId,
            @RequestParam(required = false) Integer extraNewCards,
            @RequestParam(required = false) Integer extraDueReviews,
            Authentication authentication) {
        return queueService.getQueue(categoryId, subjectId, sourceId, extraNewCards, extraDueReviews, authentication);
    }

    @GetMapping("/test-queue")
    public List<TestQueueItemResponse> getTestQueue(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long sourceId,
            @RequestParam(required = false) Integer extraNewCards,
            @RequestParam(required = false) Integer extraDueReviews,
            Authentication authentication) {
        return queueService.getTestQueue(categoryId, subjectId, sourceId, extraNewCards, extraDueReviews, authentication);
    }

    @GetMapping("/queue/new-cards-available")
    public NewCardsAvailableResponse getAvailableNewCards(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long sourceId,
            Authentication authentication) {
        return new NewCardsAvailableResponse(
                queueService.countAvailableNewCards(categoryId, subjectId, sourceId, authentication));
    }

    /** Zaległości w wybranym zakresie i trybie (FLASHCARD albo TEST): ile czeka i ile nie zmieści się dziś w sesji. */
    @GetMapping("/queue/backlog")
    public BacklogResponse getBacklog(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long sourceId,
            @RequestParam(defaultValue = "FLASHCARD") ReviewMode mode,
            Authentication authentication) {
        return queueService.getBacklog(categoryId, subjectId, sourceId, mode, authentication);
    }

    /** Sesja testowa z podanych pytań (np. "Przerób błędy teraz" po egzaminie próbnym), w podanej kolejności. */
    @GetMapping("/practice-queue")
    public List<TestQueueItemResponse> getPracticeQueue(
            @RequestParam List<Long> cardIds, Authentication authentication) {
        return practiceQueueService.queue(cardIds, authentication);
    }

    /** "Powtórz moje błędy": sesja z pytań, w których ostatnia odpowiedź w teście była błędna. */
    @GetMapping("/mistakes-queue")
    public List<TestQueueItemResponse> getMistakesQueue(Authentication authentication) {
        return mistakesService.queue(authentication);
    }

    @GetMapping("/mistakes-count")
    public MistakesCountResponse getMistakesCount(Authentication authentication) {
        return mistakesService.count(authentication);
    }

    @GetMapping("/due-count")
    public DueCountResponse getDueCount(Authentication authentication) {
        return new DueCountResponse(queueService.countDueNow(authentication));
    }

    @PostMapping
    public ResponseEntity<Void> submitReview(
            @Valid @RequestBody SubmitReviewRequest request, Authentication authentication) {
        reviewService.submitReview(request, authentication);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/test-answer")
    public TestAnswerResultResponse submitTestAnswer(
            @Valid @RequestBody SubmitTestAnswerRequest request,
            Authentication authentication) {
        return reviewService.submitTestAnswer(request, authentication);
    }
}