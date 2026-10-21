package pl.gradusiuris.backend.admin;

import org.junit.jupiter.api.Test;
import pl.gradusiuris.backend.admin.dto.ImportFormat;
import pl.gradusiuris.backend.admin.dto.ImportQuestionsRequest;
import pl.gradusiuris.backend.admin.dto.ImportReportResponse;
import pl.gradusiuris.backend.admin.dto.ImportRowResult;
import pl.gradusiuris.backend.admin.dto.ImportRowStatus;
import pl.gradusiuris.backend.legal.ActSection;
import pl.gradusiuris.backend.legal.ActSectionRepository;
import pl.gradusiuris.backend.legal.LegalAct;
import pl.gradusiuris.backend.legal.LegalActRepository;
import pl.gradusiuris.backend.question.CorrectOption;
import pl.gradusiuris.backend.question.Question;
import pl.gradusiuris.backend.question.QuestionBody;
import pl.gradusiuris.backend.question.QuestionRepository;
import pl.gradusiuris.backend.source.QuestionSource;
import pl.gradusiuris.backend.source.QuestionSourceRepository;
import pl.gradusiuris.backend.source.SourceKind;
import pl.gradusiuris.backend.testsupport.FakeRepos;
import pl.gradusiuris.backend.testsupport.InMemory;
import pl.gradusiuris.backend.track.Track;
import pl.gradusiuris.backend.track.TrackKind;
import pl.gradusiuris.backend.track.TrackRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Import pytań na prawdziwym QuestionImportService, z repozytoriami "w pamięci". */
class QuestionImportServiceTest {

    private static final String HEADER = "akt;zrodlo;rozdzial;tresc;a;b;c;poprawna;podstawa;zmiana w prawie;opis zmiany\n";

    private final List<LegalAct> acts = new ArrayList<>();
    private final List<ActSection> sections = new ArrayList<>();
    private final List<QuestionSource> sources = new ArrayList<>();
    private final List<Track> tracks = new ArrayList<>();
    private final List<Question> questions = new ArrayList<>();
    private final QuestionImportService service;

    QuestionImportServiceTest() {
        LegalAct kc = act(1L, "kodeks-cywilny", "Kodeks cywilny", "KC");
        LegalAct kk = act(2L, "kodeks-karny", "Kodeks karny", "KK");
        acts.add(kc);
        acts.add(kk);
        sections.add(section(10L, kk, "XIX. Przestępstwa przeciwko życiu i zdrowiu"));
        sections.add(section(11L, kc, "Księga pierwsza"));
        Track notarialna = track(1L, "notarialna", "Notarialna", TrackKind.EXAM);
        tracks.add(notarialna);
        tracks.add(track(2L, "komornicza", "Komornicza", TrackKind.EXAM));
        tracks.add(track(9L, "student", "Student", TrackKind.STUDENT));
        sources.add(sheet(1L, notarialna, 2024));
        QuestionSource authored = new QuestionSource();
        authored.setId(5L);
        authored.setKind(SourceKind.AUTHORED);
        authored.setSlug("autorskie");
        authored.setName("Pytania autorskie");
        sources.add(authored);

        QuestionRepository questionRepository = InMemory.repo(QuestionRepository.class, questions, Question::getId, Question::setId,
                (method, args) -> {
                    if (method.equals("findBodiesInSources")) {
                        Set<?> ids = Set.copyOf((java.util.Collection<?>) args[0]);
                        return questions.stream().filter(q -> ids.contains(q.getSource().getId())).map(QuestionImportServiceTest::bodyOf).toList();
                    }
                    throw new AssertionError("nieoczekiwane: " + method);
                });
        LegalActRepository actRepository = InMemory.repo(LegalActRepository.class, acts, LegalAct::getId, LegalAct::setId,
                (method, args) -> { throw new AssertionError("nieoczekiwane: " + method); });
        ActSectionRepository sectionRepository = InMemory.repo(ActSectionRepository.class, sections, ActSection::getId, ActSection::setId,
                (method, args) -> {
                    if (method.equals("findByActOrdered")) {
                        return sections.stream().filter(s -> s.getLegalAct().getId().equals(args[0])).toList();
                    }
                    throw new AssertionError("nieoczekiwane: " + method);
                });
        QuestionSourceRepository sourceRepository = InMemory.repo(QuestionSourceRepository.class, sources, QuestionSource::getId, QuestionSource::setId,
                (method, args) -> {
                    if (method.equals("countBySlugValue")) {
                        return sources.stream().filter(s -> s.getSlug().equals(args[0])).count();
                    }
                    throw new AssertionError("nieoczekiwane: " + method);
                });
        TrackRepository trackRepository = FakeRepos.proxy(TrackRepository.class, (method, args) -> {
            if (method.equals("findAllByOrderBySortOrderAscIdAsc")) {
                return new ArrayList<>(tracks);
            }
            throw new AssertionError("nieoczekiwane: " + method);
        });
        service = new QuestionImportService(questionRepository, actRepository, sectionRepository, sourceRepository, trackRepository);
    }

    // ------------------------------------------------------------------------------------------------ pomocnicze

    private static LegalAct act(Long id, String slug, String name, String shortName) {
        LegalAct act = new LegalAct();
        act.setId(id);
        act.setSlug(slug);
        act.setName(name);
        act.setShortName(shortName);
        return act;
    }

    private static ActSection section(Long id, LegalAct act, String name) {
        ActSection section = new ActSection();
        section.setId(id);
        section.setLegalAct(act);
        section.setName(name);
        return section;
    }

    private static Track track(Long id, String slug, String shortName, TrackKind kind) {
        Track track = new Track();
        track.setId(id);
        track.setSlug(slug);
        track.setName("Aplikacja " + shortName);
        track.setShortName(shortName);
        track.setKind(kind);
        return track;
    }

    private static QuestionSource sheet(Long id, Track track, int year) {
        QuestionSource source = new QuestionSource();
        source.setId(id);
        source.setKind(SourceKind.EXAM_SHEET);
        source.setTrack(track);
        source.setExamYear(year);
        source.setSlug(track.getSlug() + "-" + year);
        source.setName(track.getShortName() + " " + year);
        return source;
    }

    private static QuestionBody bodyOf(Question q) {
        return new QuestionBody() {
            @Override public Long getId() { return q.getId(); }
            @Override public Long getSourceId() { return q.getSource().getId(); }
            @Override public String getBody() { return q.getBody(); }
        };
    }

    private ImportReportResponse csv(String content, boolean dryRun) {
        return service.run(new ImportQuestionsRequest(ImportFormat.CSV, content, dryRun, null, null));
    }

    private void existingQuestion(Long id, QuestionSource source, String body) {
        Question q = new Question();
        q.setId(id);
        q.setLegalAct(acts.get(0));
        q.setSource(source);
        q.setBody(body);
        questions.add(q);
    }

    // ----------------------------------------------------------------------------------------------------- testy

    @Test
    void preview_reportsEveryRow_andSavesNothing_evenWithoutTheDryRunFlag() {
        String file = HEADER + "kc;autorskie;;Kto?;a;b;c;A;art. 1;;\n" + "kodeks karny;Pytania autorskie;;Co?;a;b;c;c;;;\n";

        ImportReportResponse byDefault = service.run(new ImportQuestionsRequest(ImportFormat.CSV, file, null, null, null));
        ImportReportResponse explicit = csv(file, true);

        assertThat(byDefault.dryRun()).isTrue();
        assertThat(byDefault.saved()).isFalse();
        assertThat(explicit.total()).isEqualTo(2);
        assertThat(explicit.ok()).isEqualTo(2);
        assertThat(explicit.created()).isZero();
        assertThat(questions).isEmpty();
    }

    @Test
    void save_createsTheQuestions_withActSourceSectionAndOptionalFields() {
        String file = HEADER
                + "kodeks-karny;autorskie;XIX. przestępstwa przeciwko życiu i zdrowiu;Kto zabija?;a;b;c;b;art. 148 KK;01.10.2026;nowelizacja\n";

        ImportReportResponse report = csv(file, false);

        assertThat(report.saved()).isTrue();
        assertThat(report.created()).isEqualTo(1);
        Question q = questions.get(0);
        assertThat(q.getLegalAct().getSlug()).isEqualTo("kodeks-karny");
        assertThat(q.getSource().getSlug()).isEqualTo("autorskie");
        assertThat(q.getSection().getId()).isEqualTo(10L);
        assertThat(q.getCorrectOption()).isEqualTo(CorrectOption.B);
        assertThat(q.getLegalBasis()).isEqualTo("art. 148 KK");
        assertThat(q.getLawChangedAt()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(q.getLawChangeNote()).isEqualTo("nowelizacja");
        assertThat(q.isHidden()).isFalse();
    }

    @Test
    void anyErrorBlocksTheWholeFile_andEveryErrorIsReportedAtOnce() {
        String file = HEADER
                + "kc;autorskie;;Dobre?;a;b;c;A;;;\n"
                + "nie-ma-takiego;autorskie;;Zły akt?;a;b;c;A;;;\n"
                + "kc;autorskie;;Zła litera?;a;b;c;D;;;\n"
                + "kc;autorskie;Nieznany rozdział;Zły rozdział?;a;b;c;A;;;\n"
                + "kc;autorskie;;Zła data?;a;b;c;A;;31.31.2026;\n";

        ImportReportResponse report = csv(file, false);

        assertThat(report.saved()).isFalse();
        assertThat(report.created()).isZero();
        assertThat(report.ok()).isEqualTo(1);
        assertThat(report.errors()).isEqualTo(4);
        assertThat(questions).isEmpty();
        List<ImportRowResult> errors = report.rows().stream().filter(r -> r.status() == ImportRowStatus.ERROR).toList();
        assertThat(errors).extracting(ImportRowResult::row).containsExactly(3, 4, 5, 6);
        assertThat(errors.get(0).message()).contains("Nie ma aktu prawnego");
        assertThat(errors.get(1).message()).contains("D");
        assertThat(errors.get(2).message()).contains("rozdziału");
        assertThat(errors.get(3).message()).contains("Nieprawidłowa data");
        assertThat(errors.get(0).preview()).isEqualTo("Zły akt?");
    }

    @Test
    void duplicates_areSkippedNotErrors_bothAgainstTheDatabaseAndWithinTheFile() {
        existingQuestion(77L, sources.get(1), "Kto  jest właścicielem?");
        String file = HEADER
                + "kc;autorskie;;kto jest właścicielem?;a;b;c;A;;;\n"
                + "kc;autorskie;;Nowe pytanie?;a;b;c;A;;;\n"
                + "kc;Pytania autorskie;;Nowe  pytanie?;a;b;c;A;;;\n"
                + "kc;notarialna-2024;;Nowe pytanie?;a;b;c;A;;;\n";

        ImportReportResponse report = csv(file, false);

        assertThat(report.saved()).isTrue();
        assertThat(report.duplicates()).isEqualTo(2);
        assertThat(report.created()).isEqualTo(2);
        assertThat(report.rows().get(0).status()).isEqualTo(ImportRowStatus.DUPLICATE);
        assertThat(report.rows().get(0).message()).contains("77");
        assertThat(report.rows().get(2).status()).isEqualTo(ImportRowStatus.DUPLICATE);
        assertThat(report.rows().get(2).message()).contains("wierszu 3");
        assertThat(report.rows().get(3).status()).isEqualTo(ImportRowStatus.OK);
        assertThat(questions).hasSize(3);
    }

    @Test
    void sourceCanBeAnExistingSheet_byNameOrAddress_orAnApplicationWithYear() {
        String file = HEADER
                + "kc;Notarialna 2024;;P1?;a;b;c;A;;;\n"
                + "kc;notarialna-2024;;P2?;a;b;c;A;;;\n"
                + "kc;notarialna 2024;;P3?;a;b;c;A;;;\n";

        ImportReportResponse report = csv(file, false);

        assertThat(report.newSheets()).isEmpty();
        assertThat(questions).extracting(q -> q.getSource().getId()).containsOnly(1L);
        assertThat(sources).hasSize(2);
    }

    @Test
    void aMissingSheet_isOnlyAnnouncedInThePreview_andCreatedOnceOnSave() {
        String file = HEADER
                + "kc;Komornicza 2025;;P1?;a;b;c;A;;;\n"
                + "kc;komornicza-2025;;P2?;a;b;c;A;;;\n"
                + "kc;Notarialna 2026;;P3?;a;b;c;A;;;\n";

        ImportReportResponse preview = csv(file, true);

        assertThat(preview.newSheets()).containsExactly("Komornicza 2025", "Notarialna 2026");
        assertThat(sources).hasSize(2);

        ImportReportResponse saved = csv(file, false);

        assertThat(saved.newSheets()).containsExactly("Komornicza 2025", "Notarialna 2026");
        assertThat(sources).hasSize(4);
        QuestionSource komornicza = sources.stream().filter(s -> s.getName().equals("Komornicza 2025")).findFirst().orElseThrow();
        assertThat(komornicza.getSlug()).isEqualTo("komornicza-2025");
        assertThat(komornicza.getKind()).isEqualTo(SourceKind.EXAM_SHEET);
        assertThat(komornicza.getTrack().getId()).isEqualTo(2L);
        assertThat(komornicza.getExamYear()).isEqualTo(2025);
        assertThat(komornicza.isHidden()).isFalse();
        assertThat(questions).hasSize(3);
        assertThat(questions.get(0).getSource()).isSameAs(questions.get(1).getSource());
    }

    @Test
    void aSheetIsNotCreated_whenTheFileHasAnError() {
        String file = HEADER + "kc;Komornicza 2025;;P1?;a;b;c;A;;;\n" + "kc;Komornicza 2025;;P2?;a;b;c;X;;;\n";

        ImportReportResponse report = csv(file, false);

        assertThat(report.saved()).isFalse();
        assertThat(sources).hasSize(2);
        assertThat(questions).isEmpty();
    }

    @Test
    void unknownSources_theStudentApplication_andAmbiguousNamesAreErrors() {
        sources.add(sheet(8L, tracks.get(1), 2024));
        sources.get(2).setName("Notarialna 2024");
        String file = HEADER
                + "kc;Nieznane;;P1?;a;b;c;A;;;\n"
                + "kc;Student 2025;;P2?;a;b;c;A;;;\n"
                + "kc;Notarialna 2024;;P3?;a;b;c;A;;;\n"
                + "kc;Notarialna 1800;;P4?;a;b;c;A;;;\n";

        ImportReportResponse report = csv(file, true);

        assertThat(report.errors()).isEqualTo(4);
        assertThat(report.rows().get(0).message()).contains("Nie ma źródła");
        assertThat(report.rows().get(1).message()).contains("Nie ma źródła");
        assertThat(report.rows().get(2).message()).contains("kilku źródeł");
        assertThat(report.rows().get(3).message()).contains("Nie ma źródła");
    }

    @Test
    void defaults_fillMissingActAndSource_butARowValueWins() {
        String file = "tresc;a;b;c;poprawna;akt\nP1?;a;b;c;A;\nP2?;a;b;c;B;kodeks-karny\n";

        ImportReportResponse report = service.run(new ImportQuestionsRequest(ImportFormat.CSV, file, false, "kc", "autorskie"));

        assertThat(report.saved()).isTrue();
        assertThat(questions).extracting(q -> q.getLegalAct().getSlug()).containsExactly("kodeks-cywilny", "kodeks-karny");
        assertThat(questions).extracting(q -> q.getSource().getSlug()).containsOnly("autorskie");
    }

    @Test
    void withoutActOrSourceTheRowsAreErrors_notACrash() {
        ImportReportResponse report = csv("tresc;a;b;c;poprawna\nP?;a;b;c;A\n", true);

        assertThat(report.errors()).isEqualTo(1);
        assertThat(report.rows().get(0).message()).contains("akt");
    }

    @Test
    void json_importsWithDefaultsFromTheFile_andReportsArowProblem() {
        String file = """
                {"akt":"kodeks-cywilny","zrodlo":"autorskie","pytania":[
                  {"tresc":"Pytanie JSON?","options":["a","b","c"],"poprawna":"c","podstawa":"art. 3"},
                  {"tresc":"Złe pytanie?","options":["a","b"],"poprawna":"a"}]}
                """;

        ImportReportResponse preview = service.run(new ImportQuestionsRequest(ImportFormat.JSON, file, true, null, null));

        assertThat(preview.ok()).isEqualTo(1);
        assertThat(preview.errors()).isEqualTo(1);
        assertThat(preview.rows().get(1).message()).contains("trzy odpowiedzi");

        String good = """
                {"akt":"kodeks-cywilny","zrodlo":"autorskie","pytania":[{"tresc":"Pytanie JSON?","options":["a","b","c"],"poprawna":"c)"}]}
                """;
        ImportReportResponse saved = service.run(new ImportQuestionsRequest(ImportFormat.JSON, good, false, null, null));

        assertThat(saved.saved()).isTrue();
        assertThat(questions.get(0).getCorrectOption()).isEqualTo(CorrectOption.C);
    }

    @Test
    void aStructuralFileProblem_isABadRequest() {
        assertThatThrownBy(() -> csv("a;b\n1;2\n", true)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("brakuje kolumn");
        assertThatThrownBy(() -> service.run(new ImportQuestionsRequest(ImportFormat.JSON, "[", true, null, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("JSON");
    }

    @Test
    void theReportListIsShortened_butCountsCoverTheWholeFile_andErrorsComeFirst() {
        StringBuilder file = new StringBuilder(HEADER);
        for (int i = 0; i < 700; i++) {
            file.append("kc;autorskie;;Pytanie ").append(i).append("?;a;b;c;A;;;\n");
        }
        file.append("kc;autorskie;;Zły?;a;b;c;Q;;;\n");

        ImportReportResponse report = csv(file.toString(), true);

        assertThat(report.total()).isEqualTo(701);
        assertThat(report.ok()).isEqualTo(700);
        assertThat(report.errors()).isEqualTo(1);
        assertThat(report.rowsTruncated()).isTrue();
        assertThat(report.rows()).hasSize(QuestionImportService.MAX_REPORT_ROWS);
        assertThat(report.rows().get(report.rows().size() - 1).status()).isEqualTo(ImportRowStatus.ERROR);
    }

    @Test
    void thePreviewOfALongBodyIsCut() {
        String body = "Bardzo ".repeat(40);

        ImportReportResponse report = csv(HEADER + "kc;autorskie;;" + body + ";a;b;c;A;;;\n", true);

        assertThat(report.rows().get(0).preview()).hasSize(QuestionImportService.PREVIEW_LENGTH + 1).endsWith("…");
    }

    @Test
    void ignoredColumnsAreReported() {
        ImportReportResponse report = csv(HEADER.replace("\n", ";kolor\n") + "kc;autorskie;;P?;a;b;c;A;;;;zielony\n", true);

        assertThat(report.ignoredColumns()).containsExactly("kolor");
        assertThat(report.ok()).isEqualTo(1);
    }
}
