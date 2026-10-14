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
    private final CustomTestService customTestService;

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

    /**
     * Kolejka testu. {@code extraNewCards=0} oznacza sesję bez nowych pytań (same zaległe powtórki, do dziennego limitu):
     * z niej korzysta „Powtórz zaległe" na stronie Testy. Bez parametru do zaległych dochodzą nowe pytania w dziennym limicie.
     */
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
            @RequestParam(defaultValue = "FLASHCARD") ReviewMode mode,
            Authentication authentication) {
        return new NewCardsAvailableResponse(
                queueService.countAvailableNewCards(categoryId, subjectId, sourceId, mode, authentication));
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

    /**
     * Własny test, krok 1: ile pytań każdego rodzaju (wszystkie, nowe, błędy, zaległe) jest w wybranym zakresie.
     * Zakres to suma rodzajów ({@code categoryIds}) i aktów prawnych ({@code subjectIds}); bez żadnego cały materiał.
     * Lata egzaminu ({@code yearFrom}, {@code yearTo}) są opcjonalne.
     */
    @GetMapping("/custom-test/counts")
    public CustomTestCountsResponse getCustomTestCounts(
            @RequestParam(required = false) List<Long> categoryIds,
            @RequestParam(required = false) List<Long> subjectIds,
            @RequestParam(required = false) Integer yearFrom,
            @RequestParam(required = false) Integer yearTo,
            Authentication authentication) {
        return customTestService.counts(categoryIds, subjectIds, yearFrom, yearTo, authentication);
    }

    /**
     * Własny test, krok 2: losuje numery pytań. Sesję otwiera się potem przez {@code /practice-queue}. Niczego nie zapisuje.
     */
    @PostMapping("/custom-test")
    public CustomTestSelectionResponse drawCustomTest(
            @Valid @RequestBody CustomTestRequest request, Authentication authentication) {
        return customTestService.select(request, authentication);
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

    /** Ile kart czeka na powtórkę w trybie FLASHCARD (domyślnie: fiszki) albo TEST (pytania testowe). */
    @GetMapping("/due-count")
    public DueCountResponse getDueCount(
            @RequestParam(defaultValue = "FLASHCARD") ReviewMode mode, Authentication authentication) {
        return new DueCountResponse(queueService.countDueNow(mode, authentication));
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