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
    private final CustomTestSessionService customTestSessionService;

    // fieldId, actId i sourceId są opcjonalne (od najwęższego: źródło, akt prawny, dziedzina).
    // Bez żadnego z nich kolejka obejmuje całą pulę użytkownika.
    @GetMapping("/flashcard-queue")
    public List<FlashcardQueueItemResponse> getFlashcardQueue(
            @RequestParam(required = false) Long fieldId,
            @RequestParam(required = false) Long actId,
            @RequestParam(required = false) Long sourceId,
            @RequestParam(required = false) Integer extraNewCards,
            @RequestParam(required = false) Integer extraDueReviews,
            Authentication authentication) {
        return queueService.getFlashcardQueue(fieldId, actId, sourceId, extraNewCards, extraDueReviews, authentication);
    }

    /**
     * Kolejka testu. {@code extraNewCards=0} oznacza sesję bez nowych pytań (same zaległe powtórki, do dziennego limitu):
     * z niej korzysta „Powtórz zaległe" na stronie Testy. Bez parametru do zaległych dochodzą nowe pytania w dziennym limicie.
     */
    @GetMapping("/test-queue")
    public List<TestQueueItemResponse> getTestQueue(
            @RequestParam(required = false) Long fieldId,
            @RequestParam(required = false) Long actId,
            @RequestParam(required = false) Long sourceId,
            @RequestParam(required = false) Integer extraNewCards,
            @RequestParam(required = false) Integer extraDueReviews,
            Authentication authentication) {
        return queueService.getTestQueue(fieldId, actId, sourceId, extraNewCards, extraDueReviews, authentication);
    }

    @GetMapping("/queue/new-available")
    public NewCardsAvailableResponse getAvailableNewCards(
            @RequestParam(required = false) Long fieldId,
            @RequestParam(required = false) Long actId,
            @RequestParam(required = false) Long sourceId,
            @RequestParam(defaultValue = "FLASHCARD") ReviewMode mode,
            Authentication authentication) {
        return new NewCardsAvailableResponse(
                queueService.countAvailableNewQuestions(fieldId, actId, sourceId, mode, authentication));
    }

    /** Zaległości w wybranym zakresie i trybie (FLASHCARD albo TEST): ile czeka i ile nie zmieści się dziś w sesji. */
    @GetMapping("/queue/backlog")
    public BacklogResponse getBacklog(
            @RequestParam(required = false) Long fieldId,
            @RequestParam(required = false) Long actId,
            @RequestParam(required = false) Long sourceId,
            @RequestParam(defaultValue = "FLASHCARD") ReviewMode mode,
            Authentication authentication) {
        return queueService.getBacklog(fieldId, actId, sourceId, mode, authentication);
    }

    /** Sesja testowa z podanych pytań (np. "Przerób błędy teraz" po egzaminie próbnym), w podanej kolejności. */
    @GetMapping("/practice-queue")
    public List<TestQueueItemResponse> getPracticeQueue(
            @RequestParam List<Long> questionIds, Authentication authentication) {
        return practiceQueueService.queue(questionIds, authentication);
    }

    /**
     * Własny test, krok 1: ile pytań każdego rodzaju (wszystkie, nowe, błędy, zaległe) jest w wybranym zakresie.
     * Zakres to suma dziedzin ({@code fieldIds}), aktów prawnych ({@code actIds}) i przedmiotów studenta
     * ({@code courseIds}); bez żadnego cała pula.
     */
    @GetMapping("/custom-test/counts")
    public CustomTestCountsResponse getCustomTestCounts(
            @RequestParam(required = false) List<Long> fieldIds,
            @RequestParam(required = false) List<Long> actIds,
            @RequestParam(required = false) List<Long> courseIds,
            Authentication authentication) {
        return customTestService.counts(fieldIds, actIds, courseIds, authentication);
    }

    /**
     * Własny test, krok 2: losuje numery pytań i zapisuje test ("Moje testy"; odpowiedź ma jego numer w
     * {@code sessionId}). Sesję otwiera się potem przez {@code /practice-queue}, a odpowiedzi wysyła z tym numerem.
     */
    @PostMapping("/custom-test")
    public CustomTestSelectionResponse drawCustomTest(
            @Valid @RequestBody CustomTestRequest request, Authentication authentication) {
        return customTestSessionService.draw(request, authentication);
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

    /** Ile pytań czeka na powtórkę w trybie FLASHCARD (domyślnie: fiszki) albo TEST (pytania testowe). */
    @GetMapping("/due-count")
    public DueCountResponse getDueCount(
            @RequestParam(defaultValue = "FLASHCARD") ReviewMode mode, Authentication authentication) {
        return new DueCountResponse(queueService.countDueNow(mode, authentication));
    }

    /**
     * Ile pytań trybu (FLASHCARD albo TEST) wróci na powtórkę do końca jutra, razem z tymi, które czekają już teraz.
     * Do podsumowania sesji ("Jutro wróci N powtórek").
     */
    @GetMapping("/due-tomorrow")
    public DueCountResponse getDueByEndOfTomorrow(
            @RequestParam(defaultValue = "FLASHCARD") ReviewMode mode, Authentication authentication) {
        return new DueCountResponse(queueService.countDueByEndOfTomorrow(mode, authentication));
    }

    /** Ocena fiszki. W odpowiedzi: czy to była pierwsza ocena tej fiszki (zaczęła się uczyć właśnie teraz). */
    @PostMapping
    public ReviewResultResponse submitReview(
            @Valid @RequestBody SubmitReviewRequest request, Authentication authentication) {
        return new ReviewResultResponse(reviewService.submitReview(request, authentication));
    }

    @PostMapping("/test-answer")
    public TestAnswerResultResponse submitTestAnswer(
            @Valid @RequestBody SubmitTestAnswerRequest request,
            Authentication authentication) {
        return reviewService.submitTestAnswer(request, authentication);
    }
}