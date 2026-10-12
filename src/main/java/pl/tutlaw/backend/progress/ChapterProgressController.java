package pl.tutlaw.backend.progress;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pl.tutlaw.backend.progress.dto.SetProgressRequest;

@RestController
@RequiredArgsConstructor
public class ChapterProgressController {

    private final ChapterProgressService progressService;

    @PutMapping("/api/chapters/{chapterId}/progress")
    public ResponseEntity<Void> setProgress(@PathVariable Long chapterId, @RequestBody SetProgressRequest request, Authentication authentication) {
        progressService.setProgress(chapterId, request.isRead(), authentication);
        return ResponseEntity.ok().build();
    }
}