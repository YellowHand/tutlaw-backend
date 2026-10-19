package pl.tutlaw.backend.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.tutlaw.backend.catalog.dto.TestCatalogResponse;

@RestController
@RequestMapping("/api/tests")
@RequiredArgsConstructor
public class TestCatalogController {

    private final TestCatalogService testCatalogService;

    /** Dziedzina → akt prawny oraz źródła (arkusze i pytania autorskie), z licznikami pytań dla zalogowanego użytkownika. */
    @GetMapping("/catalog")
    public TestCatalogResponse getCatalog(Authentication authentication) {
        return testCatalogService.getCatalog(authentication);
    }
}
