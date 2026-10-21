package pl.tutlaw.backend.admin;

import org.junit.jupiter.api.Test;
import pl.tutlaw.backend.admin.dto.AdminSectionResponse;
import pl.tutlaw.backend.common.exception.ConflictException;
import pl.tutlaw.backend.common.exception.ResourceNotFoundException;
import pl.tutlaw.backend.legal.ActSection;
import pl.tutlaw.backend.legal.ActSectionRepository;
import pl.tutlaw.backend.legal.LegalAct;
import pl.tutlaw.backend.legal.LegalActRepository;
import pl.tutlaw.backend.question.QuestionRepository;
import pl.tutlaw.backend.testsupport.FakeRepos;
import pl.tutlaw.backend.testsupport.InMemory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Rozdziały aktów w panelu administratora na prawdziwym AdminSectionService, z repozytoriami "w pamięci". */
class AdminSectionServiceTest {

    private final List<LegalAct> acts = new ArrayList<>();
    private final List<ActSection> sections = new ArrayList<>();
    private final Map<Long, long[]> questionsBySection = new HashMap<>();
    private final AdminSectionService service;

    AdminSectionServiceTest() {
        acts.add(act(1L, "Kodeks karny"));
        acts.add(act(2L, "Kodeks cywilny"));
        sections.add(section(1L, acts.get(0), "Rozdział I. Zasady odpowiedzialności karnej", 10));
        sections.add(section(2L, acts.get(0), "Rozdział II. Formy popełnienia przestępstwa", 20));
        sections.add(section(3L, acts.get(1), "Księga pierwsza. Przepisy ogólne", 10));

        ActSectionRepository sectionRepository = InMemory.repo(ActSectionRepository.class, sections, ActSection::getId, ActSection::setId, (method, args) -> {
            assertThat(method).isEqualTo("findByActOrdered");
            return sections.stream().filter(s -> s.getLegalAct().getId().equals(args[0]))
                    .sorted(Comparator.comparingInt(ActSection::getSortOrder).thenComparing(ActSection::getId)).toList();
        });
        LegalActRepository actRepository = InMemory.repo(LegalActRepository.class, acts, LegalAct::getId, LegalAct::setId,
                (method, args) -> { throw new AssertionError("nieoczekiwane: " + method); });
        QuestionRepository questionRepository = FakeRepos.proxy(QuestionRepository.class, (method, args) -> switch (method) {
            case "totalsBySection" -> questionsBySection.entrySet().stream().map(e -> totals(e.getKey(), e.getValue()[0], e.getValue()[1])).toList();
            case "countInSection" -> questionsBySection.containsKey(args[0]) ? questionsBySection.get(args[0])[0] : 0L;
            default -> throw new AssertionError("nieoczekiwane: " + method);
        });
        service = new AdminSectionService(sectionRepository, actRepository, questionRepository);
    }

    private static LegalAct act(Long id, String name) {
        LegalAct act = new LegalAct();
        act.setId(id);
        act.setName(name);
        return act;
    }

    private static ActSection section(Long id, LegalAct act, String name, int order) {
        ActSection section = new ActSection();
        section.setId(id);
        section.setLegalAct(act);
        section.setName(name);
        section.setSortOrder(order);
        return section;
    }

    private static QuestionRepository.GroupTotals totals(Long id, long total, long hidden) {
        return new QuestionRepository.GroupTotals() {
            @Override public Long getGroupId() { return id; }
            @Override public Long getTotal() { return total; }
            @Override public Long getHiddenTotal() { return hidden; }
        };
    }

    private static List<String> names(List<AdminSectionResponse> list) {
        return list.stream().map(AdminSectionResponse::name).toList();
    }

    @Test
    void list_isInOrder_withQuestionCountsOfThatActOnly() {
        questionsBySection.put(2L, new long[]{40, 3});

        List<AdminSectionResponse> list = service.list(1L);

        assertThat(names(list)).containsExactly("Rozdział I. Zasady odpowiedzialności karnej", "Rozdział II. Formy popełnienia przestępstwa");
        assertThat(list.get(1).questionCount()).isEqualTo(40);
        assertThat(list.get(1).hiddenQuestionCount()).isEqualTo(3);
        assertThat(list.get(0).questionCount()).isZero();
    }

    @Test
    void list_ofAMissingAct_isNotFound() {
        assertThatThrownBy(() -> service.list(404L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_appendsAtTheEnd_andRefusesADuplicateIgnoringCaseAndSpaces() {
        List<AdminSectionResponse> list = service.create(1L, "  Rozdział III.   Wyłączenie odpowiedzialności ");

        assertThat(list).hasSize(3);
        assertThat(list.get(2).name()).isEqualTo("Rozdział III. Wyłączenie odpowiedzialności");
        assertThat(list.get(2).sortOrder()).isEqualTo(30);
        assertThatThrownBy(() -> service.create(1L, "ROZDZIAŁ III. WYŁĄCZENIE ODPOWIEDZIALNOŚCI"))
                .isInstanceOf(ConflictException.class);
        // ta sama nazwa w innym akcie jest dozwolona
        assertThat(service.create(2L, "Rozdział III. Wyłączenie odpowiedzialności")).hasSize(2);
    }

    @Test
    void createMany_addsInTheGivenOrder_skippingBlanksAndDuplicates() {
        List<AdminSectionResponse> list = service.createMany(1L, Arrays.asList(
                "Rozdział III. A", "", "  ", "rozdział iii. a", "Rozdział IV. B", "Rozdział I. Zasady odpowiedzialności karnej"));

        assertThat(names(list)).containsExactly(
                "Rozdział I. Zasady odpowiedzialności karnej", "Rozdział II. Formy popełnienia przestępstwa",
                "Rozdział III. A", "Rozdział IV. B");
        assertThat(list.get(3).sortOrder()).isGreaterThan(list.get(2).sortOrder());
    }

    @Test
    void createMany_withNothingNewIsABadRequest() {
        assertThatThrownBy(() -> service.createMany(1L, List.of("", "Rozdział I. Zasady odpowiedzialności karnej")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(sections).hasSize(3);
    }

    @Test
    void rename_changesTheName_andRefusesOneTakenByAnotherSectionOfTheSameAct() {
        List<AdminSectionResponse> list = service.rename(2L, "Rozdział II. Formy zjawiskowe");

        assertThat(names(list)).contains("Rozdział II. Formy zjawiskowe");
        assertThat(service.rename(2L, "Rozdział II. Formy zjawiskowe")).hasSize(2);
        assertThatThrownBy(() -> service.rename(2L, "Rozdział I. Zasady odpowiedzialności karnej"))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.rename(404L, "X")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reorder_setsTheOrderOfAllSectionsOfTheAct() {
        List<AdminSectionResponse> list = service.reorder(1L, List.of(2L, 1L));

        assertThat(list).extracting(AdminSectionResponse::id).containsExactly(2L, 1L);
        assertThat(sections.get(2).getSortOrder()).isEqualTo(10); // inny akt bez zmian
    }

    @Test
    void reorder_refusesAListThatIsNotExactlyTheSectionsOfTheAct() {
        assertThatThrownBy(() -> service.reorder(1L, List.of(1L))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.reorder(1L, List.of(1L, 2L, 3L))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.reorder(1L, List.of(1L, 1L))).isInstanceOf(IllegalArgumentException.class);
        assertThat(sections.get(0).getSortOrder()).isEqualTo(10);
    }

    @Test
    void delete_removesASectionWithoutQuestions_andReturnsTheRemainingList() {
        List<AdminSectionResponse> list = service.delete(2L);

        assertThat(list).extracting(AdminSectionResponse::id).containsExactly(1L);
    }

    @Test
    void delete_refusesASectionThatHasQuestions() {
        questionsBySection.put(2L, new long[]{1, 1});

        assertThatThrownBy(() -> service.delete(2L)).isInstanceOf(ConflictException.class);
        assertThat(sections).hasSize(3);
    }
}
