package pl.tutlaw.backend.source;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pl.tutlaw.backend.source.dto.SourceRequest;
import pl.tutlaw.backend.source.dto.SourceResponse;

import java.util.List;


@RestController
@RequiredArgsConstructor
public class SourceController {

    private final SourceService sourceService;

    @GetMapping("/api/subjects/{subjectId}/sources")
    public List<SourceResponse> getBySubject(@PathVariable Long subjectId) {
        return sourceService.getBySubject(subjectId);
    }

    @PostMapping("/api/sources")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SourceResponse> create(@Valid @RequestBody SourceRequest request) {
        return ResponseEntity.ok(sourceService.create(request));
    }
}