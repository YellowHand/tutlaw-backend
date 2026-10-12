package pl.tutlaw.backend.statistics;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.tutlaw.backend.statistics.dto.*;

import java.util.List;

@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    @GetMapping("/overview")
    public OverviewResponse getOverview(@RequestParam(required = false) Long subjectId, Authentication authentication) {
        return statisticsService.getOverview(subjectId, authentication);
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
            @RequestParam Long sourceId, Authentication authentication) {
        return statisticsService.getMasteryBySource(sourceId, authentication);
    }

    @GetMapping("/sources")
    public List<SourceProgressResponse> getSourceProgress(@RequestParam Long subjectId, Authentication authentication) {
        return statisticsService.getSourceProgress(subjectId, authentication);
    }


    @GetMapping("/tests")
    public TestStatsResponse getTestStats(Authentication authentication) {
        return statisticsService.getTestStats(authentication);
    }

    @GetMapping("/activity")
    public List<ActivityHeatmapItem> getActivity(
            @RequestParam(defaultValue = "30") int days,
            Authentication authentication
    ) {
        return statisticsService.getActivity(days, authentication);
    }
}