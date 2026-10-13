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
    private final OriginStatisticsService originStatisticsService;

    /** Przegląd nauki. {@code mode}: FLASHCARD (tylko fiszki), TEST (tylko pytania testowe), bez trybu: cały materiał. */
    @GetMapping("/overview")
    public OverviewResponse getOverview(
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) ReviewMode mode,
            Authentication authentication) {
        return statisticsService.getOverview(subjectId, mode, authentication);
    }

    @GetMapping("/weak-cards")
    public List<WeakCardResponse> getWeakCards(
            @RequestParam(required = false) Long subjectId,
            @RequestParam(defaultValue = "10") int limit,
            Authentication authentication
    ) {
        return statisticsService.getWeakCards(subjectId, limit, authentication);
    }

    @GetMapping("/chapters")
    public List<ChapterMasteryResponse> getChapterMastery(
            @RequestParam Long sourceId,
            @RequestParam(required = false) ReviewMode mode,
            Authentication authentication) {
        return statisticsService.getMasteryBySource(sourceId, mode, authentication);
    }

    @GetMapping("/sources")
    public List<SourceProgressResponse> getSourceProgress(
            @RequestParam Long subjectId,
            @RequestParam(required = false) ReviewMode mode,
            Authentication authentication) {
        return statisticsService.getSourceProgress(subjectId, mode, authentication);
    }


    @GetMapping("/tests")
    public TestStatsResponse getTestStats(Authentication authentication) {
        return statisticsService.getTestStats(authentication);
    }

    /** Statystyki Testów według pochodzenia pytań i roku egzaminu (wszystkie pochodzenia, bez filtra z Testów). */
    @GetMapping("/origins")
    public OriginStatsResponse getOriginStats(Authentication authentication) {
        return originStatisticsService.getOriginStats(authentication);
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