package pl.gradusiuris.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Profil dev: plik bazowy jest „ostry” (produkcyjny) i bez zmiennych środowiskowych nie wystartuje. Test wymaga działającej bazy (docker compose up -d).
@ActiveProfiles("dev")
@SpringBootTest
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
