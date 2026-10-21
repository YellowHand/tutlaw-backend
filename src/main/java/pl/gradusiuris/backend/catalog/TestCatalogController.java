package pl.gradusiuris.backend.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.gradusiuris.backend.catalog.dto.TestCatalogResponse;
import pl.gradusiuris.backend.pool.SourceMode;

@RestController
@RequestMapping("/api/tests")
@RequiredArgsConstructor
public class TestCatalogController {

    private final TestCatalogService testCatalogService;

    /**
     * Dziedzina → akt prawny → rozdział, z licznikami pytań dla zalogowanego użytkownika. {@code source} to źródło
     * pytań, które liczymy (ALL, MINISTRY albo AUTHORED; bez niego ALL).
     */
    @GetMapping("/catalog")
    public TestCatalogResponse getCatalog(
            @RequestParam(required = false) SourceMode source, Authentication authentication) {
        return testCatalogService.getCatalog(source, authentication);
    }
}
