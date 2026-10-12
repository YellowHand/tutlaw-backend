package pl.tutlaw.backend.chapter;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pl.tutlaw.backend.chapter.dto.ChapterRequest;
import pl.tutlaw.backend.chapter.dto.ChapterResponse;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ChapterController {

    private final ChapterService chapterService;

    @GetMapping("/api/sources/{sourceId}/chapters")
    public List<ChapterResponse> getBySource(@PathVariable Long sourceId, Authentication authentication) {
        return chapterService.getBySource(sourceId, authentication);
    }

    @PostMapping("/api/chapters")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ChapterResponse> create(@Valid @RequestBody ChapterRequest request) {
        return ResponseEntity.ok(chapterService.create(request));
    }
}