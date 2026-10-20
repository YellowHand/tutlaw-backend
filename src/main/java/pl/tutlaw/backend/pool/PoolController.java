package pl.tutlaw.backend.pool;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.tutlaw.backend.pool.dto.PoolResponse;
import pl.tutlaw.backend.pool.dto.UpdatePoolActsRequest;
import pl.tutlaw.backend.pool.dto.UpdatePoolCoursesRequest;
import pl.tutlaw.backend.pool.dto.UpdateSourceFilterRequest;

/** Pula pytań zalogowanego użytkownika: odczyt, zawężenie do aktów (student: do przedmiotów) i filtr źródeł (zapisane na koncie). */
@RestController
@RequestMapping("/api/me/pool")
@RequiredArgsConstructor
public class PoolController {

    private final PoolSettingsService service;

    @GetMapping
    public PoolResponse get(Authentication authentication) {
        return service.get(authentication);
    }

    /** Zawęża pulę do wybranych aktów prawnych; pusta lista zdejmuje zawężenie. */
    @PutMapping("/acts")
    public PoolResponse updateActs(@Valid @RequestBody UpdatePoolActsRequest request, Authentication authentication) {
        return service.updateActs(request, authentication);
    }

    /** Student wybiera przedmioty, z których się uczy; pusta lista zdejmuje wybór (wszystkie przedmioty). */
    @PutMapping("/courses")
    public PoolResponse updateCourses(
            @Valid @RequestBody UpdatePoolCoursesRequest request, Authentication authentication) {
        return service.updateCourses(request, authentication);
    }

    /** Filtr źródeł pytań w Testach. */
    @PutMapping("/source-filter")
    public PoolResponse updateSourceFilter(
            @Valid @RequestBody UpdateSourceFilterRequest request, Authentication authentication) {
        return service.updateSourceFilter(request, authentication);
    }
}
