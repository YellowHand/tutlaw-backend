package pl.tutlaw.backend.admin;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pl.tutlaw.backend.admin.dto.AdminSectionOrderRequest;
import pl.tutlaw.backend.admin.dto.AdminSectionRequest;
import pl.tutlaw.backend.admin.dto.AdminSectionResponse;
import pl.tutlaw.backend.admin.dto.AdminSectionsBulkRequest;

import java.util.List;

/** Rozdziały aktów (np. „Rozdział XIX. ..." w Kodeksie karnym). Tylko administrator. Każda zmiana zwraca pełną, aktualną listę rozdziałów aktu. */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSectionController {

    private final AdminSectionService service;

    @GetMapping("/acts/{actId}/sections")
    public List<AdminSectionResponse> list(@PathVariable Long actId) {
        return service.list(actId);
    }

    @PostMapping("/acts/{actId}/sections")
    @ResponseStatus(HttpStatus.CREATED)
    public List<AdminSectionResponse> create(@PathVariable Long actId, @Valid @RequestBody AdminSectionRequest request) {
        return service.create(actId, request.name());
    }

    /** Wiele rozdziałów naraz (jeden na pozycję listy), na koniec listy; powtórki i puste pozycje są pomijane. */
    @PostMapping("/acts/{actId}/sections/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    public List<AdminSectionResponse> createMany(@PathVariable Long actId, @Valid @RequestBody AdminSectionsBulkRequest request) {
        return service.createMany(actId, request.names());
    }

    /** Nowa kolejność: numery wszystkich rozdziałów aktu w żądanej kolejności. */
    @PutMapping("/acts/{actId}/sections/order")
    public List<AdminSectionResponse> reorder(@PathVariable Long actId, @Valid @RequestBody AdminSectionOrderRequest request) {
        return service.reorder(actId, request.ids());
    }

    @PutMapping("/sections/{id}")
    public List<AdminSectionResponse> rename(@PathVariable Long id, @Valid @RequestBody AdminSectionRequest request) {
        return service.rename(id, request.name());
    }

    /** Usuwa rozdział bez pytań. Rozdział z pytaniami: 409. */
    @DeleteMapping("/sections/{id}")
    public List<AdminSectionResponse> delete(@PathVariable Long id) {
        return service.delete(id);
    }
}
