package pl.tutlaw.backend.category;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pl.tutlaw.backend.category.dto.CategoryRequest;
import pl.tutlaw.backend.category.dto.CategoryResponse;
import pl.tutlaw.backend.subject.SubjectService;
import pl.tutlaw.backend.subject.dto.SubjectResponse;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;
    private final SubjectService subjectService;

    @GetMapping
    public List<CategoryResponse> getAll() {
        return categoryService.getAll();
    }

    @GetMapping("/{categoryId}/subjects")
    public List<SubjectResponse> getSubjectsByCategory(@PathVariable Long categoryId) {
        return subjectService.getByCategory(categoryId);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(categoryService.create(request));
    }
}