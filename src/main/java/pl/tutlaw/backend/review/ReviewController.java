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

    @GetMapping("/queue")
    public List<ReviewQueueItemResponse> getQueue(
            @RequestParam Long subjectId,
            @RequestParam(required = false) Long sourceId,
            @RequestParam(required = false) Integer extraNewCards,
            Authentication authentication) {
        return queueService.getQueue(subjectId, sourceId, extraNewCards, authentication);
    }

    @GetMapping("/test-queue")
    public List<TestQueueItemResponse> getTestQueue(
            @RequestParam Long subjectId,
            @RequestParam(required = false) Long sourceId,
            @RequestParam(required = false) Integer extraNewCards,
            Authentication authentication) {
        return queueService.getTestQueue(subjectId, sourceId, extraNewCards, authentication);
    }

    @GetMapping("/queue/new-cards-available")
    public NewCardsAvailableResponse getAvailableNewCards(
            @RequestParam Long subjectId,
            @RequestParam(required = false) Long sourceId,
            Authentication authentication) {
        return new NewCardsAvailableResponse(queueService.countAvailableNewCards(subjectId, sourceId, authentication));
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