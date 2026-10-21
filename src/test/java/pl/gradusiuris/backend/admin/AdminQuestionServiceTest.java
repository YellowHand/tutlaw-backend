package pl.gradusiuris.backend.admin;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import pl.gradusiuris.backend.admin.dto.AdminQuestionRequest;
import pl.gradusiuris.backend.admin.dto.AdminQuestionResponse;
import pl.gradusiuris.backend.common.PageParams;
import pl.gradusiuris.backend.common.PageResponse;
import pl.gradusiuris.backend.common.exception.ConflictException;
import pl.gradusiuris.backend.common.exception.ResourceNotFoundException;
import pl.gradusiuris.backend.legal.ActSection;
import pl.gradusiuris.backend.legal.ActSectionRepository;
import pl.gradusiuris.backend.legal.LegalAct;
import pl.gradusiuris.backend.legal.LegalActRepository;
import pl.gradusiuris.backend.mark.MarkOrigin;
import pl.gradusiuris.backend.mark.MarkStatus;
import pl.gradusiuris.backend.mark.QuestionMark;
import pl.gradusiuris.backend.mark.QuestionMarkRepository;
import pl.gradusiuris.backend.mark.QuestionMarkType;
import pl.gradusiuris.backend.question.CorrectOption;
import pl.gradusiuris.backend.question.Question;
import pl.gradusiuris.backend.question.QuestionBody;
import pl.gradusiuris.backend.question.QuestionRepository;
import pl.gradusiuris.backend.report.QuestionReportRepository;
import pl.gradusiuris.backend.source.QuestionSource;
import pl.gradusiuris.backend.source.QuestionSourceRepository;
import pl.gradusiuris.backend.testsupport.FakeRepos;
import pl.gradusiuris.backend.testsupport.InMemory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pytania w panelu administratora na prawdziwym AdminQuestionService, z repozytoriami "w pamięci". */
class AdminQuestionServiceTest {

    private final List<LegalAct> acts = new ArrayList<>();
    private final List<ActSection> sections = new ArrayList<>();
    private final List<QuestionSource> sources = new ArrayList<>();
    private final List<Question> questions = new ArrayList<>();
    private final List<QuestionMark> marks = new ArrayList<>();
    private Object[] listArgs;
    private final AdminQuestionService service;

    AdminQuestionServiceTest() {
        LegalAct kc = new LegalAct();
        kc.setId(1L);
        kc.setName("Kodeks cywilny");
        kc.setShortName("KC");
        LegalAct kk = new LegalAct();
        kk.setId(2L);
        kk.setName("Kodeks karny");
        kk.setShortName("KK");
        acts.add(kc);
        acts.add(kk);
        ActSection book = new ActSection();
        book.setId(10L);
        book.setLegalAct(kk);
        book.setName("Rozdział XIX");
        sections.add(book);
        QuestionSource authored = new QuestionSource();
        authored.setId(5L);
        authored.setName("Pytania autorskie");
        authored.setKind(pl.gradusiuris.backend.source.SourceKind.AUTHORED);
        authored.setSlug("autorskie");
        sources.add(authored);

        QuestionRepository questionRepository = InMemory.repo(QuestionRepository.class, questions, Question::getId, Question::setId,
                (method, args) -> switch (method) {
                    case "findForAdminById" -> questions.stream().filter(q -> q.getId().equals(args[0])).findFirst();
                    case "findBodiesInSources" -> questions.stream().map(AdminQuestionServiceTest::bodyOf).toList();
                    case "findForAdmin" -> {
                        listArgs = args;
                        yield new PageImpl<>(new ArrayList<>(questions), (Pageable) args[args.length - 1], questions.size());
                    }
                    default -> throw new AssertionError("nieoczekiwane: " + method);
                });
        LegalActRepository actRepository = InMemory.repo(LegalActRepository.class, acts, LegalAct::getId, LegalAct::setId,
                (method, args) -> { throw new AssertionError("nieoczekiwane: " + method); });
        ActSectionRepository sectionRepository = InMemory.repo(ActSectionRepository.class, sections, ActSection::getId, ActSection::setId,
                (method, args) -> { throw new AssertionError("nieoczekiwane: " + method); });
        QuestionSourceRepository sourceRepository = InMemory.repo(QuestionSourceRepository.class, sources, QuestionSource::getId, QuestionSource::setId,
                (method, args) -> { throw new AssertionError("nieoczekiwane: " + method); });
        QuestionMarkRepository markRepository = FakeRepos.proxy(QuestionMarkRepository.class, (method, args) -> {
            if (method.equals("findAllByQuestionIds")) {
                return marks.stream().filter(m -> ((java.util.Collection<?>) args[0]).contains(m.getQuestionId())).toList();
            }
            throw new AssertionError("nieoczekiwane: " + method);
        });
        QuestionReportRepository reportRepository = FakeRepos.proxy(QuestionReportRepository.class, (method, args) -> {
            if (method.equals("countOpenByQuestion")) {
                return List.of(InMemory.group(100L, 3));
            }
            throw new AssertionError("nieoczekiwane: " + method);
        });
        service = new AdminQuestionService(questionRepository, actRepository, sectionRepository, sourceRepository, markRepository, reportRepository);
    }

    private static QuestionBody bodyOf(Question q) {
        return new QuestionBody() {
            @Override public Long getId() { return q.getId(); }
            @Override public Long getSourceId() { return q.getSource().getId(); }
            @Override public String getBody() { return q.getBody(); }
        };
    }

    private static AdminQuestionRequest request(String body, Long actId, Long sectionId) {
        return new AdminQuestionRequest(actId, sectionId, 5L, body, "Tak", "Nie", "Może", CorrectOption.A,
                "Wyjaśnienie", "art. 1 KC", LocalDate.of(2026, 1, 1), null, null);
    }

    private Question stored(Long id, String body) {
        Question q = new Question();
        q.setId(id);
        q.setLegalAct(acts.get(0));
        q.setSource(sources.get(0));
        q.setBody(body);
        q.setOptionA("a");
        q.setOptionB("b");
        q.setOptionC("c");
        q.setCorrectOption(CorrectOption.C);
        questions.add(q);
        return q;
    }

    @Test
    void create_savesAVisibleQuestion_withTheGivenPlacement() {
        AdminQuestionResponse created = service.create(request("  Kto jest właścicielem?  ", 2L, 10L));

        assertThat(questions).hasSize(1);
        assertThat(created.id()).isNotNull();
        assertThat(created.body()).isEqualTo("Kto jest właścicielem?");
        assertThat(created.actId()).isEqualTo(2L);
        assertThat(created.actShortName()).isEqualTo("KK");
        assertThat(created.sectionName()).isEqualTo("Rozdział XIX");
        assertThat(created.sourceName()).isEqualTo("Pytania autorskie");
        assertThat(created.correctOption()).isEqualTo(CorrectOption.A);
        assertThat(created.legalBasis()).isEqualTo("art. 1 KC");
        assertThat(created.validAsOf()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(created.hidden()).isFalse();
        assertThat(created.flashcardHidden()).isFalse();
    }

    @Test
    void create_refusesASectionOfAnotherAct_unknownReferences_andBadContent() {
        assertThatThrownBy(() -> service.create(request("P?", 1L, 10L)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Rozdział nie należy");
        assertThatThrownBy(() -> service.create(request("P?", 99L, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("aktu");
        assertThatThrownBy(() -> service.create(request("P?", 1L, 99L)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("rozdziału");
        assertThatThrownBy(() -> service.create(new AdminQuestionRequest(1L, null, 99L, "P?", "a", "b", "c", CorrectOption.A, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("źródła");
        assertThatThrownBy(() -> service.create(request(" ", 1L, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("treść");
        assertThat(questions).isEmpty();
    }

    @Test
    void create_refusesTheSameQuestionInTheSameSource_ignoringSpacingAndCase() {
        stored(3L, "Kto  jest właścicielem?");

        assertThatThrownBy(() -> service.create(request("kto jest właścicielem?", 1L, null)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("3");
        assertThat(questions).hasSize(1);
    }

    @Test
    void update_changesTheQuestionInPlace_keepsItsVisibility_andMayClearTheSection() {
        Question q = stored(3L, "Stara treść?");
        q.setSection(sections.get(0));
        q.setHidden(true);
        q.setFlashcardHidden(true);

        AdminQuestionResponse updated = service.update(3L, request("Nowa treść?", 1L, null));

        assertThat(updated.id()).isEqualTo(3L);
        assertThat(updated.body()).isEqualTo("Nowa treść?");
        assertThat(updated.actId()).isEqualTo(1L);
        assertThat(updated.sectionId()).isNull();
        assertThat(updated.hidden()).isTrue();
        assertThat(updated.flashcardHidden()).isTrue();
        assertThat(questions).hasSize(1);
    }

    @Test
    void update_mayKeepItsOwnBody_butNotTakeAnotherQuestionsBody() {
        stored(3L, "Pierwsze?");
        stored(4L, "Drugie?");

        assertThat(service.update(3L, request("Pierwsze?", 1L, null)).body()).isEqualTo("Pierwsze?");
        assertThatThrownBy(() -> service.update(3L, request("Drugie?", 1L, null))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.update(99L, request("X?", 1L, null))).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void get_showsMarksAndTheNumberOfOpenReports() {
        stored(100L, "Pytanie?");
        marks.add(mark(100L, QuestionMarkType.TRAP_NEGATION, MarkStatus.CONFIRMED));
        marks.add(mark(100L, QuestionMarkType.TIME_LIMIT, MarkStatus.SUGGESTED));
        marks.add(mark(101L, QuestionMarkType.TIME_LIMIT, MarkStatus.CONFIRMED));

        AdminQuestionResponse response = service.get(100L);

        assertThat(response.openReports()).isEqualTo(3);
        assertThat(response.marks()).extracting(m -> m.mark() + ":" + m.status())
                .containsExactly("TRAP_NEGATION:CONFIRMED", "TIME_LIMIT:SUGGESTED");
        assertThatThrownBy(() -> service.get(5L)).isInstanceOf(ResourceNotFoundException.class);
    }

    private static QuestionMark mark(Long questionId, QuestionMarkType type, MarkStatus status) {
        QuestionMark m = new QuestionMark();
        m.setQuestionId(questionId);
        m.setMark(type);
        m.setStatus(status);
        m.setOrigin(MarkOrigin.AUTO);
        return m;
    }

    @Test
    void list_withoutFilters_asksForEverything_andPassesThePage() {
        stored(100L, "Pytanie?");

        PageResponse<AdminQuestionResponse> page = service.list(
                new AdminQuestionFilter(null, null, null, null, null, false, false, null, null), PageParams.of(2, 20));

        assertThat(page.items()).hasSize(1);
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(20);
        assertThat(listArgs[0]).isEqualTo("%");
        assertThat(listArgs[1]).isEqualTo(-1L);
        assertThat(listArgs[2]).isEqualTo(true);   // anyAct
        assertThat(listArgs[4]).isEqualTo(true);   // anySection
        assertThat(listArgs[6]).isEqualTo(false);  // withoutSection
        assertThat(listArgs[7]).isEqualTo(true);   // anySource
        assertThat(listArgs[9]).isEqualTo(true);   // anyVisibility
        assertThat(listArgs[11]).isEqualTo(false); // onlyReported
        assertThat(listArgs[12]).isEqualTo(true);  // anyMark
        assertThat(((Pageable) listArgs[15]).getPageNumber()).isEqualTo(2);
        assertThat(((Pageable) listArgs[15]).getPageSize()).isEqualTo(20);
    }

    @Test
    void list_translatesEveryFilterIntoQueryArguments() {
        stored(100L, "Pytanie?");

        service.list(new AdminQuestionFilter(" 50%_off! ", 1L, 10L, 5L, true, true, true,
                QuestionMarkType.TRAP_NUMBERS, null), PageParams.of(0, 50));

        assertThat(listArgs[0]).isEqualTo("%50!%!_off!!%");
        assertThat(listArgs[1]).isEqualTo(-1L);
        assertThat(listArgs[2]).isEqualTo(false);
        assertThat(listArgs[3]).isEqualTo(1L);
        assertThat(listArgs[4]).isEqualTo(false);
        assertThat(listArgs[5]).isEqualTo(10L);
        assertThat(listArgs[6]).isEqualTo(true);
        assertThat(listArgs[7]).isEqualTo(false);
        assertThat(listArgs[8]).isEqualTo(5L);
        assertThat(listArgs[9]).isEqualTo(false);
        assertThat(listArgs[10]).isEqualTo(true);
        assertThat(listArgs[11]).isEqualTo(true);
        assertThat(listArgs[12]).isEqualTo(false);
        assertThat(listArgs[13]).isEqualTo(QuestionMarkType.TRAP_NUMBERS);
        assertThat(listArgs[14]).isEqualTo(MarkStatus.CONFIRMED);
    }

    @Test
    void list_aNumberInTheSearchBoxAlsoMatchesTheQuestionId() {
        stored(100L, "Pytanie?");

        service.list(new AdminQuestionFilter("#123", null, null, null, null, false, false, null, null), PageParams.of(0, 50));
        assertThat(listArgs[1]).isEqualTo(123L);
        assertThat(listArgs[0]).isEqualTo("%#123%");

        service.list(new AdminQuestionFilter("art. 415", null, null, null, null, false, false, null, null), PageParams.of(0, 50));
        assertThat(listArgs[1]).isEqualTo(-1L);
    }

    @Test
    void sources_listsTheAuthoredOneFirst_thenSheetsFromTheNewestYear_includingHiddenOnes() {
        sources.add(sheetSource(6L, "Notarialna 2023", 2023, false));
        sources.add(sheetSource(7L, "Notarialna 2025", 2025, true));
        sources.add(sheetSource(8L, "Komornicza 2025", 2025, false));

        var list = service.sources();

        assertThat(list).extracting(s -> s.name()).containsExactly("Pytania autorskie", "Komornicza 2025", "Notarialna 2025", "Notarialna 2023");
        assertThat(list.get(2).hidden()).isTrue();
        assertThat(list.get(0).kind()).isEqualTo(pl.gradusiuris.backend.source.SourceKind.AUTHORED);
    }

    private static QuestionSource sheetSource(Long id, String name, int year, boolean hidden) {
        QuestionSource s = new QuestionSource();
        s.setId(id);
        s.setName(name);
        s.setSlug(name.toLowerCase().replace(' ', '-'));
        s.setKind(pl.gradusiuris.backend.source.SourceKind.EXAM_SHEET);
        s.setExamYear(year);
        s.setHidden(hidden);
        return s;
    }

    @Test
    void likePattern_lowercasesAndEscapesWildcards() {
        assertThat(AdminQuestionService.likePattern("KC Art_5%")).isEqualTo("%kc art!_5!%%");
        assertThat(Optional.of(AdminQuestionService.likePattern("a!b"))).contains("%a!!b%");
    }
}
