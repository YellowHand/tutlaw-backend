package pl.tutlaw.backend.admin;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pl.tutlaw.backend.admin.dto.ActIdsRequest;
import pl.tutlaw.backend.admin.dto.AdminCourseRequest;
import pl.tutlaw.backend.admin.dto.AdminCourseResponse;

import java.util.List;

/** Przedmioty Studenta (np. Prawo karne, Prawoznawstwo) i ich akty. Tylko administrator. */
@RestController
@RequestMapping("/api/admin/courses")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminCourseController {

    private final AdminCourseService service;

    @GetMapping
    public List<AdminCourseResponse> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminCourseResponse create(@Valid @RequestBody AdminCourseRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public AdminCourseResponse update(@PathVariable Long id, @Valid @RequestBody AdminCourseRequest request) {
        return service.update(id, request);
    }

    /** Usuwa przedmiot i zdejmuje go z wyborów studentów (akty i pytania zostają). */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    /** Zastępuje listę aktów i zagadnień przedmiotu. */
    @PutMapping("/{id}/acts")
    public AdminCourseResponse setActs(@PathVariable Long id, @Valid @RequestBody ActIdsRequest request) {
        return service.setActs(id, request.actIds());
    }
}
