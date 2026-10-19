package pl.tutlaw.backend.statistics;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.tutlaw.backend.review.ReviewMode;
import pl.tutlaw.backend.statistics.dto.*;

import java.util.List;

@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;
    private final SourceStatisticsService sourceStatisticsService;
    private final LearningSummaryService learningSummaryService;

    /** Przegląd nauki. {@code mode}: FLASHCARD (tylko fiszki), TEST (tylko testy), bez trybu: oba razem. */
    @GetMapping("/overview")
    public OverviewResponse getOverview(
            @RequestParam(required = false) Long actId,
            @RequestParam(required = false) ReviewMode mode,
            Authentication authentication) {
        return statisticsService.getOverview(actId, mode, authentication);
    }

    @GetMapping("/weak-questions")
    public List<WeakQuestionResponse> getWeakQuestions(
            @RequestParam(required = false) Long actId,
            @RequestParam(required = false) ReviewMode mode,
            @RequestParam(defaultValue = "10") int limit,
            Authentication authentication
    ) {
        return statisticsService.getWeakQuestions(actId, mode, limit, authentication);
    }

    /** Opanowanie aktów prawnych z puli użytkownika. */
    @GetMapping("/acts")
    public List<ActMasteryResponse> getActMastery(
            @RequestParam(required = false) ReviewMode mode,
            Authentication authentication) {
        return statisticsService.getMasteryByAct(mode, authentication);
    }

    @GetMapping("/tests")
    public TestStatsResponse getTestStats(Authentication authentication) {
        return statisticsService.getTestStats(authentication);
    }

    /** Statystyki Testów według źródła pytań i roku egzaminu (cała pula ścieżki, bez filtra z Testów). */
    @GetMapping("/sources")
    public SourceStatsResponse getSourceStats(Authentication authentication) {
        return sourceStatisticsService.getSourceStats(authentication);
    }

    /** "Podsumowanie nauki": liczba odpowiedzi, egzaminów i własnych testów, czas nauki i średni czas odpowiedzi. */
    @GetMapping("/learning-summary")
    public LearningSummaryResponse getLearningSummary(Authentication authentication) {
        return learningSummaryService.get(authentication);
    }

    @GetMapping("/activity")
    public List<ActivityHeatmapItem> getActivity(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) ReviewMode mode,
            Authentication authentication
    ) {
        return statisticsService.getActivity(days, mode, authentication);
    }
}
