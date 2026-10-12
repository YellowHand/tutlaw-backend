package pl.tutlaw.backend.catalog;

import org.junit.jupiter.api.Test;
import pl.tutlaw.backend.catalog.CatalogAssembler.CategoryRow;
import pl.tutlaw.backend.catalog.CatalogAssembler.SourceRow;
import pl.tutlaw.backend.catalog.CatalogAssembler.SubjectRow;
import pl.tutlaw.backend.catalog.dto.TestCatalogResponse;
import pl.tutlaw.backend.catalog.dto.TestCatalogResponse.CategoryNode;
import pl.tutlaw.backend.catalog.dto.TestCatalogResponse.SourceNode;
import pl.tutlaw.backend.catalog.dto.TestCatalogResponse.SubjectNode;
import pl.tutlaw.backend.common.PolishSort;
import pl.tutlaw.backend.source.SourceType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogAssemblerTest {

    private static final CategoryRow STUDENT = new CategoryRow(1L, "Student", "student", "Przedmioty", 5);
    private static final CategoryRow KARNE = new CategoryRow(2L, "Prawo karne i postępowanie karne", "karne", "Akty prawne", 30);
    private static final CategoryRow CYWILNE = new CategoryRow(3L, "Prawo cywilne i postępowanie cywilne", "cywilne", "Akty prawne", 20);

    private static final SubjectRow KK = new SubjectRow(10L, 2L, "Kodeks karny", "kk");
    private static final SubjectRow KKS = new SubjectRow(11L, 2L, "Kodeks karny skarbowy", "kks");
    private static final SubjectRow ZARYS = new SubjectRow(12L, 1L, "Żywienie zwierząt", "zz");
    private static final SubjectRow ADM = new SubjectRow(13L, 1L, "Administracyjne", "adm");

    private static final SourceRow KK_POOL = new SourceRow(100L, 10L, "Egzaminy z lat ubiegłych", SourceType.FLAT_POOL);
    private static final SourceRow KK_BOOK = new SourceRow(101L, 10L, "Podręcznik", SourceType.TEXTBOOK);

    private static TestCatalogResponse assemble() {
        return CatalogAssembler.assemble(
                List.of(KARNE, STUDENT, CYWILNE),
                List.of(KKS, KK, ZARYS, ADM),
                List.of(KK_BOOK, KK_POOL),
                Map.of(100L, 862L, 101L, 240L),
                Map.of(100L, 862L, 101L, 40L),
                Map.of(100L, 12L, 101L, 5L));
    }

    @Test
    void categoriesAreOrderedBySortOrderThenName() {
        List<String> slugs = assemble().categories().stream().map(CategoryNode::slug).toList();

        assertThat(slugs).containsExactly("student", "cywilne", "karne");
    }

    @Test
    void subjectsAreGroupedUnderTheirCategory_andSortedByPolishAlphabet() {
        CategoryNode student = assemble().categories().get(0);

        assertThat(student.subjects().stream().map(SubjectNode::name).toList())
                .containsExactly("Administracyjne", "Żywienie zwierząt");
    }

    @Test
    void sourceCountsSeparateAvailableSolvedAndLocked() {
        CategoryNode karne = assemble().categories().get(2);
        SubjectNode kk = karne.subjects().get(0);
        SourceNode pool = kk.sources().get(0);
        SourceNode book = kk.sources().get(1);

        assertThat(kk.name()).isEqualTo("Kodeks karny");
        assertThat(pool.questionCount()).isEqualTo(862);
        assertThat(pool.solvedCount()).isEqualTo(12);
        assertThat(pool.lockedCount()).isEqualTo(0);
        assertThat(book.questionCount()).isEqualTo(40);
        assertThat(book.solvedCount()).isEqualTo(5);
        assertThat(book.lockedCount()).isEqualTo(200);
    }

    @Test
    void subjectAndCategoryTotalsAreSumsOfTheirChildren() {
        CategoryNode karne = assemble().categories().get(2);
        SubjectNode kk = karne.subjects().get(0);

        assertThat(kk.questionCount()).isEqualTo(902);
        assertThat(kk.solvedCount()).isEqualTo(17);
        assertThat(kk.lockedCount()).isEqualTo(200);
        assertThat(karne.questionCount()).isEqualTo(902);
        assertThat(karne.solvedCount()).isEqualTo(17);
        assertThat(karne.lockedCount()).isEqualTo(200);
    }

    @Test
    void emptyCategoryAndSubjectWithoutSourcesAreKeptWithZeroCounts() {
        CategoryNode cywilne = assemble().categories().get(1);
        SubjectNode kks = assemble().categories().get(2).subjects().get(1);

        assertThat(cywilne.subjects()).isEmpty();
        assertThat(cywilne.questionCount()).isEqualTo(0);
        assertThat(kks.name()).isEqualTo("Kodeks karny skarbowy");
        assertThat(kks.sources()).isEmpty();
        assertThat(kks.questionCount()).isEqualTo(0);
    }

    @Test
    void solvedNeverExceedsAvailable_evenWhenTheCountsDisagree() {
        TestCatalogResponse response = CatalogAssembler.assemble(
                List.of(KARNE), List.of(KK), List.of(KK_POOL),
                Map.of(100L, 10L), Map.of(100L, 10L), Map.of(100L, 25L));

        SourceNode pool = response.categories().get(0).subjects().get(0).sources().get(0);
        assertThat(pool.solvedCount()).isEqualTo(10);
    }

    @Test
    void availableNeverExceedsTotal_andLockedNeverGoesNegative() {
        TestCatalogResponse response = CatalogAssembler.assemble(
                List.of(KARNE), List.of(KK), List.of(KK_POOL),
                Map.of(100L, 10L), Map.of(100L, 14L), Map.of());

        SourceNode pool = response.categories().get(0).subjects().get(0).sources().get(0);
        assertThat(pool.questionCount()).isEqualTo(10);
        assertThat(pool.lockedCount()).isEqualTo(0);
    }

    @Test
    void sourceWithoutAnyCountsHasZeros() {
        TestCatalogResponse response = CatalogAssembler.assemble(
                List.of(KARNE), List.of(KK), List.of(KK_POOL), Map.of(), Map.of(), Map.of());

        SourceNode pool = response.categories().get(0).subjects().get(0).sources().get(0);
        assertThat(pool.questionCount()).isEqualTo(0);
        assertThat(pool.solvedCount()).isEqualTo(0);
        assertThat(pool.lockedCount()).isEqualTo(0);
    }

    @Test
    void polishSort_putsDiacriticLettersAfterTheirBaseLetters() {
        List<String> sorted = List.of("Zasady", "Łowiectwo", "Cywilne", "Ćwiczenia", "Lasy", "Żywność").stream()
                .sorted(PolishSort.byName()).toList();

        assertThat(sorted).containsExactly("Cywilne", "Ćwiczenia", "Lasy", "Łowiectwo", "Zasady", "Żywność");
    }
}
