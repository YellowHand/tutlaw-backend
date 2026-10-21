package pl.gradusiuris.backend.review;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CustomTestScopeLabelTest {

    @Test
    void noSelectionMeansTheWholePool() {
        assertThat(CustomTestScopeLabel.describe(List.of())).isEqualTo("Cała pula");
    }

    @Test
    void upToThreeNamesAreListed() {
        assertThat(CustomTestScopeLabel.describe(List.of("Kodeks karny"))).isEqualTo("Kodeks karny");
        assertThat(CustomTestScopeLabel.describe(List.of("Kodeks karny", "Kodeks cywilny")))
                .isEqualTo("Kodeks karny i Kodeks cywilny");
        assertThat(CustomTestScopeLabel.describe(List.of("A", "B", "C"))).isEqualTo("A, B i C");
    }

    @Test
    void moreNamesAreCountedWithThePolishForm() {
        assertThat(CustomTestScopeLabel.describe(List.of("A", "B", "C", "D"))).isEqualTo("A, B i 2 inne");
        assertThat(CustomTestScopeLabel.describe(List.of("A", "B", "C", "D", "E", "F"))).isEqualTo("A, B i 4 inne");
        assertThat(CustomTestScopeLabel.describe(List.of("A", "B", "C", "D", "E", "F", "G"))).isEqualTo("A, B i 5 innych");
        assertThat(CustomTestScopeLabel.describe(java.util.Collections.nCopies(14, "X"))).endsWith("i 12 innych");
        assertThat(CustomTestScopeLabel.describe(java.util.Collections.nCopies(24, "X"))).endsWith("i 22 inne");
    }

    @Test
    void aVeryLongLabelIsTruncatedToTheColumnSize() {
        String longName = "x".repeat(200);

        String label = CustomTestScopeLabel.describe(List.of(longName, longName));

        assertThat(label).hasSize(300).endsWith("…");
    }
}
