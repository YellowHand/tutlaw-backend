package pl.gradusiuris.backend.track;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.gradusiuris.backend.track.dto.TrackSummary;

import java.util.List;

/** Lista ścieżek do wyboru (pięć aplikacji i Student) w kolejności z bazy. */
@RestController
@RequestMapping("/api/tracks")
@RequiredArgsConstructor
public class TrackController {

    private final TrackRepository repository;

    @GetMapping
    @Transactional(readOnly = true)
    public List<TrackSummary> list() {
        return repository.findAllByOrderBySortOrderAscIdAsc().stream().map(TrackSummary::of).toList();
    }
}
