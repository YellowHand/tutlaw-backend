package pl.gradusiuris.backend.common.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionSafetyCheckTest {

    private static final String GOOD_SECRET = "Zx9fQ2mK7pLw4RtYb8NcVd3HgJs6UeA1o5Xi0Pq";

    @Test
    void acceptsAProperProductionSetup() {
        assertThat(ProductionSafetyCheck.problems(GOOD_SECRET, "https://gradusiuris.pl", "Tr0ng-Passw0rd-from-RDS")).isEmpty();
    }

    @Test
    void rejectsTheTemplateSecretAndShortSecrets() {
        assertThat(ProductionSafetyCheck.problems("lokalny-dev-sekret-min-32-znaki-zmien-koniecznie-na-produkcji", "https://gradusiuris.pl", "pass-9f8a7b6c"))
                .anySatisfy(p -> assertThat(p).contains("APP_JWT_SECRET"));
        assertThat(ProductionSafetyCheck.problems("krotki", "https://gradusiuris.pl", "pass-9f8a7b6c"))
                .anySatisfy(p -> assertThat(p).contains("mniej niż 32"));
        assertThat(ProductionSafetyCheck.problems(null, "https://gradusiuris.pl", "pass-9f8a7b6c")).isNotEmpty();
    }

    @Test
    void rejectsFrontendAddressWithoutHttps() {
        assertThat(ProductionSafetyCheck.problems(GOOD_SECRET, "http://localhost:5173", "pass-9f8a7b6c"))
                .hasSize(1).anySatisfy(p -> assertThat(p).contains("FRONTEND_URL"));
        assertThat(ProductionSafetyCheck.problems(GOOD_SECRET, null, "pass-9f8a7b6c")).isNotEmpty();
    }

    @Test
    void rejectsEmptyAndTemplateDatabasePassword() {
        assertThat(ProductionSafetyCheck.problems(GOOD_SECRET, "https://gradusiuris.pl", "")).isNotEmpty();
        assertThat(ProductionSafetyCheck.problems(GOOD_SECRET, "https://gradusiuris.pl", null)).isNotEmpty();
        assertThat(ProductionSafetyCheck.problems(GOOD_SECRET, "https://gradusiuris.pl", "gradusiuris_password")).isNotEmpty();
    }

    @Test
    void problemsNeverContainTheSecretItself() {
        String secret = "lokalny-dev-sekret-tylko-do-testu-0123456789";
        assertThat(String.join(" ", ProductionSafetyCheck.problems(secret, "https://gradusiuris.pl", "pass-9f8a7b6c"))).doesNotContain(secret);
    }
}
