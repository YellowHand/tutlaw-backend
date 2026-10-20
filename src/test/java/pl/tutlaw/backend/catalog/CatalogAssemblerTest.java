package pl.tutlaw.backend.catalog;

import org.junit.jupiter.api.Test;
import pl.tutlaw.backend.catalog.CatalogAssembler.ActRow;
import pl.tutlaw.backend.catalog.CatalogAssembler.FieldRow;
import pl.tutlaw.backend.catalog.CatalogAssembler.SourceRow;
import pl.tutlaw.backend.catalog.dto.TestCatalogResponse;
import pl.tutlaw.backend.catalog.dto.TestCatalogResponse.ActNode;
import pl.tutlaw.backend.catalog.dto.TestCatalogResponse.FieldNode;
import pl.tutlaw.backend.catalog.dto.TestCatalogResponse.SourceNode;
import pl.tutlaw.backend.common.PolishSort;
import pl.tutlaw.backend.legal.LegalActKind;
import pl.tutlaw.backend.pool.SourceFilter;
import pl.tutlaw.backend.source.SourceKind;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogAssemblerTest {

    private static final FieldRow STUDENT = new FieldRow(1L, "Student", "student", 90);
    private static final FieldRow KARNE = new FieldRow(2L, "Prawo karne i proces karny", "karne", 20);
    private static final FieldRow CYWILNE = new FieldRow(3L, "Prawo cywilne i postępowanie cywilne", "cywilne", 10);

    private static final ActRow KK = new ActRow(10L, 2L, "Kodeks karny", "k.k.", "kk", LegalActKind.ACT, 1);
    private static final ActRow KKS = new ActRow(11L, 2L, "Kodeks karny skarbowy", "k.k.s.", "kks", LegalActKind.ACT, 2);
    private static final ActRow KPK = new ActRow(12L, 2L, "Kodeks postępowania karnego", "k.p.k.", "kpk", LegalActKind.ACT, 3);
    private static final ActRow PRAWOZNAWSTWO = new ActRow(
            13L, 1L, "Prawoznawstwo", "Prawoznawstwo", "prawoznawstwo", LegalActKind.TOPIC, 1);

    private static final SourceRow EGZAMIN_NOT = new SourceRow(100L, "Egzamin notarialny 2023", SourceKind.EXAM_SHEET, 2023);
    private static final SourceRow EGZAMIN_NOT_OLD = new SourceRow(101L, "Egzamin notarialny 2021", SourceKind.EXAM_SHEET, 2021);
    private static final SourceRow EGZAMIN_KOM = new SourceRow(102L, "Egzamin komorniczy 2022", SourceKind.EXAM_SHEET, 2022);
    private static final SourceRow AUTORSKIE = new SourceRow(103L, "Pytania autorskie", SourceKind.AUTHORED, null);

    private static TestCatalogResponse assemble() {
        return CatalogAssembler.assemble(
                false,
                List.of(KARNE, STUDENT, CYWILNE),
                List.of(KKS, KK, PRAWOZNAWSTWO),
                List.of(AUTORSKIE, EGZAMIN_KOM, EGZAMIN_NOT_OLD, EGZAMIN_NOT),
                Set.of(100L, 101L),
                Map.of(10L, 100L, 11L, 30L, 13L, 20L),
                Map.of(10L, 40L, 11L, 3L),
                Map.of(100L, 70L, 101L, 40L, 102L, 25L, 103L, 15L),
                Map.of(100L, 30L, 101L, 10L, 102L, 2L));
    }

    @Test
    void fieldsWithoutActsInThePoolAreLeftOut_theRestOrderedBySortOrderThenName() {
        List<String> slugs = assemble().fields().stream().map(FieldNode::slug).toList();

        // „cywilne" nie ma aktów w puli, więc użytkownik w ogóle go nie widzi
        assertThat(slugs).containsExactly("karne", "student");
        assertThat(assemble().fields()).noneMatch(field -> field.acts().isEmpty());
    }

    @Test
    void actsAreGroupedUnderTheirField_andOnlyThoseFromThePoolAppear() {
        FieldNode karne = assemble().fields().get(0);

        // k.p.k. nie jest w puli tej ścieżki (nie ma go na liście aktów), więc nie ma go w drzewie
        assertThat(karne.acts()).extracting(ActNode::slug).containsExactly("kk", "kks");
    }

    @Test
    void topicsAreActsToo_underTheStudentField() {
        FieldNode student = assemble().fields().get(1);

        assertThat(student.acts()).hasSize(1);
        assertThat(student.acts().get(0).kind()).isEqualTo(LegalActKind.TOPIC);
        assertThat(student.acts().get(0).questionCount()).isEqualTo(20);
    }

    @Test
    void actCountsAreQuestionsAndSolvedOnes() {
        ActNode kk = assemble().fields().get(0).acts().get(0);

        assertThat(kk.name()).isEqualTo("Kodeks karny");
        assertThat(kk.shortName()).isEqualTo("k.k.");
        assertThat(kk.questionCount()).isEqualTo(100);
        assertThat(kk.solvedCount()).isEqualTo(40);
    }

    @Test
    void fieldTotalsAreSumsOfTheirActs() {
        FieldNode karne = assemble().fields().get(0);

        assertThat(karne.questionCount()).isEqualTo(130);
        assertThat(karne.solvedCount()).isEqualTo(43);
    }

    @Test
    void anActWithoutAnyCountsHasZeros() {
        TestCatalogResponse response = CatalogAssembler.assemble(
                false, List.of(KARNE), List.of(KPK), List.of(), Set.of(), Map.of(), Map.of(), Map.of(), Map.of());

        ActNode kpk = response.fields().get(0).acts().get(0);
        assertThat(kpk.questionCount()).isZero();
        assertThat(kpk.solvedCount()).isZero();
    }

    @Test
    void solvedNeverExceedsAvailable_evenWhenTheCountsDisagree() {
        TestCatalogResponse response = CatalogAssembler.assemble(
                false, List.of(KARNE), List.of(KK), List.of(EGZAMIN_NOT), Set.of(100L),
                Map.of(10L, 10L), Map.of(10L, 25L), Map.of(100L, 10L), Map.of(100L, 25L));

        assertThat(response.fields().get(0).acts().get(0).solvedCount()).isEqualTo(10);
        assertThat(response.sources().get(0).solvedCount()).isEqualTo(10);
    }

    // ---------------------------------------------------------------- źródła

    @Test
    void sourcesAreGroupedOwnSheetsFirst_thenOtherSheets_thenAuthored_newestYearFirst() {
        List<SourceNode> sources = assemble().sources();

        assertThat(sources).extracting(SourceNode::name).containsExactly(
                "Egzamin notarialny 2023", "Egzamin notarialny 2021", "Egzamin komorniczy 2022", "Pytania autorskie");
        assertThat(sources).extracting(SourceNode::group).containsExactly(
                SourceFilter.OWN_SHEETS, SourceFilter.OWN_SHEETS, SourceFilter.OTHER_SHEETS, SourceFilter.AUTHORED);
    }

    @Test
    void sourceCountsAreQuestionsAndSolvedOnes() {
        SourceNode newest = assemble().sources().get(0);

        assertThat(newest.questionCount()).isEqualTo(70);
        assertThat(newest.solvedCount()).isEqualTo(30);
        assertThat(newest.examYear()).isEqualTo(2023);
        assertThat(newest.kind()).isEqualTo(SourceKind.EXAM_SHEET);
    }

    @Test
    void aSourceWithoutQuestionsInThePoolIsLeftOut() {
        TestCatalogResponse response = CatalogAssembler.assemble(
                false, List.of(KARNE), List.of(KK), List.of(EGZAMIN_NOT, EGZAMIN_KOM), Set.of(100L),
                Map.of(), Map.of(), Map.of(100L, 5L), Map.of());

        assertThat(response.sources()).extracting(SourceNode::id).containsExactly(100L);
    }

    @Test
    void theNarrowedFlagIsPassedThrough() {
        TestCatalogResponse response = CatalogAssembler.assemble(
                true, List.of(KARNE), List.of(KK), List.of(), Set.of(), Map.of(), Map.of(), Map.of(), Map.of());

        assertThat(response.narrowed()).isTrue();
        assertThat(assemble().narrowed()).isFalse();
    }

    // ---------------------------------------------------------------- przedmioty studenta

    private static CatalogAssembler.CourseRow course(long id, String name, int sortOrder, Long... actIds) {
        return new CatalogAssembler.CourseRow(id, name, "przedmiot-" + id, sortOrder, List.of(actIds));
    }

    @Test
    void courseCountsAreTheSumOfTheirActs_andSolvedNeverExceedsTotal() {
        List<TestCatalogResponse.CourseNode> courses = CatalogAssembler.courseNodes(
                List.of(course(1, "Prawo karne", 10, 10L, 11L)),
                Map.of(10L, 100L, 11L, 30L), Map.of(10L, 40L, 11L, 90L));

        assertThat(courses).hasSize(1);
        assertThat(courses.get(0).questionCount()).isEqualTo(130);
        assertThat(courses.get(0).solvedCount()).isEqualTo(70); // 40 + min(90, 30)
        assertThat(courses.get(0).actIds()).containsExactly(10L, 11L);
    }

    @Test
    void overlappingCoursesAreCountedSeparately_andOrderedBySortOrderThenName() {
        List<TestCatalogResponse.CourseNode> courses = CatalogAssembler.courseNodes(
                List.of(course(2, "Zbiorczy", 30, 10L, 11L), course(3, "Karne", 10, 10L), course(4, "Ąbc", 10, 11L)),
                Map.of(10L, 100L, 11L, 30L), Map.of());

        assertThat(courses).extracting(TestCatalogResponse.CourseNode::name).containsExactly("Ąbc", "Karne", "Zbiorczy");
        assertThat(courses).extracting(TestCatalogResponse.CourseNode::questionCount).containsExactly(30, 100, 130);
    }

    @Test
    void aCourseWithoutQuestionsIsLeftOut() {
        List<TestCatalogResponse.CourseNode> courses = CatalogAssembler.courseNodes(
                List.of(course(1, "Pusty", 10, 10L), course(2, "Bez aktów", 20)),
                Map.of(), Map.of());

        assertThat(courses).isEmpty();
    }

    @Test
    void polishSort_putsDiacriticLettersAfterTheirBaseLetters() {
        List<String> sorted = List.of("Zasady", "Łowiectwo", "Cywilne", "Ćwiczenia", "Lasy", "Żywność").stream()
                .sorted(PolishSort.byName()).toList();

        assertThat(sorted).containsExactly("Cywilne", "Ćwiczenia", "Lasy", "Łowiectwo", "Zasady", "Żywność");
    }
}
