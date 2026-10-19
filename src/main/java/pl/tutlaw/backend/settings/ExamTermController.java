package pl.tutlaw.backend.settings;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.tutlaw.backend.settings.dto.ExamTermResponse;
import pl.tutlaw.backend.settings.dto.UpdateExamTermRequest;

import java.util.List;

/** Terminy egzaminów aplikacji: lista dla każdego zalogowanego, zmiana tylko dla administratora. */
@RestController
@RequestMapping("/api/exam-terms")
@RequiredArgsConstructor
public class ExamTermController {

    private final ExamTermService service;

    @GetMapping
    public List<ExamTermResponse> list() {
        return service.list();
    }

    @PutMapping("/{trackId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ExamTermResponse update(@PathVariable Long trackId, @Valid @RequestBody UpdateExamTermRequest request) {
        return service.update(trackId, request);
    }
}
