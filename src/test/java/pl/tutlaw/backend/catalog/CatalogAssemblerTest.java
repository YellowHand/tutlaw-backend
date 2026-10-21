package pl.tutlaw.backend.catalog;

import org.junit.jupiter.api.Test;
import pl.tutlaw.backend.catalog.CatalogAssembler.ActRow;
import pl.tutlaw.backend.catalog.CatalogAssembler.FieldRow;
import pl.tutlaw.backend.catalog.CatalogAssembler.SectionRow;
import pl.tutlaw.backend.catalog.dto.TestCatalogResponse;
import pl.tutlaw.backend.catalog.dto.TestCatalogResponse.ActNode;
import pl.tutlaw.backend.catalog.dto.TestCatalogResponse.FieldNode;
import pl.tutlaw.backend.catalog.dto.TestCatalogResponse.SectionNode;
import pl.tutlaw.backend.common.PolishSort;
import pl.tutlaw.backend.legal.LegalActKind;

import java.util.List;
import java.util.Map;

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

    private static final SectionRow KK_I = new SectionRow(200L, 10L, "I. Zasady odpowiedzialności karnej", 10);
    private static final SectionRow KK_II = new SectionRow(201L, 10L, "II. Formy popełnienia przestępstwa", 20);
    private static final SectionRow KK_XIX = new SectionRow(202L, 10L, "XIX. Przeciwko życiu i zdrowiu", 190);

    private static TestCatalogResponse assemble() {
        return CatalogAssembler.assemble(
                false,
                List.of(KARNE, STUDENT, CYWILNE),
                List.of(KKS, KK, PRAWOZNAWSTWO),
                List.of(KK_XIX, KK_I, KK_II),
                Map.of(10L, 100L, 11L, 30L, 13L, 20L),
                Map.of(10L, 40L, 11L, 3L),
                Map.of(200L, 18L, 201L, 14L, 202L, 64L));
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
                false, List.of(KARNE), List.of(KPK), List.of(), Map.of(), Map.of(), Map.of());

        ActNode kpk = response.fields().get(0).acts().get(0);
        assertThat(kpk.questionCount()).isZero();
        assertThat(kpk.solvedCount()).isZero();
    }

    @Test
    void solvedNeverExceedsAvailable_evenWhenTheCountsDisagree() {
        TestCatalogResponse response = CatalogAssembler.assemble(
                false, List.of(KARNE), List.of(KK), List.of(), Map.of(10L, 10L), Map.of(10L, 25L), Map.of());

        assertThat(response.fields().get(0).acts().get(0).solvedCount()).isEqualTo(10);
    }

    // ---------------------------------------------------------------- rozdziały

    @Test
    void sectionsAreListedUnderTheirAct_inTheOrderOfTheirSortOrder_withQuestionCounts() {
        ActNode kk = assemble().fields().get(0).acts().get(0);

        assertThat(kk.sections()).extracting(SectionNode::name).containsExactly(
                "I. Zasady odpowiedzialności karnej", "II. Formy popełnienia przestępstwa", "XIX. Przeciwko życiu i zdrowiu");
        assertThat(kk.sections()).extracting(SectionNode::questionCount).containsExactly(18, 14, 64);
        assertThat(kk.sections()).extracting(SectionNode::id).containsExactly(200L, 201L, 202L);
    }

    @Test
    void anActWithoutSectionsHasAnEmptyList() {
        ActNode kks = assemble().fields().get(0).acts().get(1);

        assertThat(kks.slug()).isEqualTo("kks");
        assertThat(kks.sections()).isEmpty();
    }

    @Test
    void aSectionWithoutQuestionsInTheChosenSourceIsLeftOut() {
        // II rozdział nie ma pytań z wybranego źródła (np. tylko autorskie), więc nic by nie dał
        TestCatalogResponse response = CatalogAssembler.assemble(
                false, List.of(KARNE), List.of(KK), List.of(KK_I, KK_II), Map.of(10L, 18L), Map.of(), Map.of(200L, 18L));

        assertThat(response.fields().get(0).acts().get(0).sections()).extracting(SectionNode::id).containsExactly(200L);
    }

    @Test
    void sectionsWithTheSameSortOrderAreOrderedByName_thenId() {
        SectionRow b = new SectionRow(301L, 10L, "B. Drugi", 50);
        SectionRow a = new SectionRow(302L, 10L, "A. Pierwszy", 50);
        SectionRow ae = new SectionRow(300L, 10L, "A. Pierwszy", 50);
        TestCatalogResponse response = CatalogAssembler.assemble(
                false, List.of(KARNE), List.of(KK), List.of(b, a, ae), Map.of(10L, 3L), Map.of(),
                Map.of(300L, 1L, 301L, 1L, 302L, 1L));

        assertThat(response.fields().get(0).acts().get(0).sections()).extracting(SectionNode::id)
                .containsExactly(300L, 302L, 301L);
    }

    @Test
    void sectionsOfAnotherActDoNotLeakIntoTheAct() {
        SectionRow foreign = new SectionRow(400L, 11L, "I. Cudzy", 10);
        TestCatalogResponse response = CatalogAssembler.assemble(
                false, List.of(KARNE), List.of(KK, KKS), List.of(KK_I, foreign), Map.of(10L, 18L, 11L, 5L), Map.of(),
                Map.of(200L, 18L, 400L, 5L));

        List<ActNode> acts = response.fields().get(0).acts();
        assertThat(acts.get(0).sections()).extracting(SectionNode::id).containsExactly(200L);
        assertThat(acts.get(1).sections()).extracting(SectionNode::id).containsExactly(400L);
    }

    @Test
    void theNarrowedFlagIsPassedThrough() {
        TestCatalogResponse response = CatalogAssembler.assemble(
                true, List.of(KARNE), List.of(KK), List.of(), Map.of(), Map.of(), Map.of());

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
