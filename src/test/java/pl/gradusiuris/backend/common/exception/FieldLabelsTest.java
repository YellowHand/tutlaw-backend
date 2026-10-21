package pl.gradusiuris.backend.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FieldLabelsTest {

    @Test
    void knownFormFieldsGetPolishLabels() {
        assertThat(FieldLabels.label("password")).isEqualTo("Hasło");
        assertThat(FieldLabels.label("privateEmail")).isEqualTo("Mail prywatny");
        assertThat(FieldLabels.label("acceptedTerms")).isEqualTo("Regulamin");
        assertThat(FieldLabels.label("reason")).isEqualTo("Powód");
        assertThat(FieldLabels.label("comment")).isEqualTo("Komentarz");
    }

    @Test
    void unknownFieldsKeepTheirCodeName() {
        assertThat(FieldLabels.label("optionA")).isEqualTo("optionA");
    }
}
