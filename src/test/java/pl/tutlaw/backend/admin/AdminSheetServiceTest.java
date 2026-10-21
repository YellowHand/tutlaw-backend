package pl.tutlaw.backend.admin;

import org.junit.jupiter.api.Test;
import pl.tutlaw.backend.admin.dto.AdminSheetCreateRequest;
import pl.tutlaw.backend.admin.dto.AdminSheetResponse;
import pl.tutlaw.backend.admin.dto.AdminSheetUpdateRequest;
import pl.tutlaw.backend.common.exception.ConflictException;
import pl.tutlaw.backend.common.exception.ResourceNotFoundException;
import pl.tutlaw.backend.question.QuestionRepository;
import pl.tutlaw.backend.source.QuestionSource;
import pl.tutlaw.backend.source.QuestionSourceRepository;
import pl.tutlaw.backend.source.SourceKind;
import pl.tutlaw.backend.testsupport.FakeRepos;
import pl.tutlaw.backend.testsupport.InMemory;
import pl.tutlaw.backend.track.Track;
import pl.tutlaw.backend.track.TrackKind;
import pl.tutlaw.backend.track.TrackRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Arkusze egzaminacyjne w panelu administratora na prawdziwym AdminSheetService, z repozytoriami "w pamięci". */
class AdminSheetServiceTest {

    private final List<Track> tracks = new ArrayList<>();
    private final List<QuestionSource> sources = new ArrayList<>();
    private final Map<Long, long[]> questionsBySource = new HashMap<>();
    private final AdminSheetService service;

    AdminSheetServiceTest() {
        tracks.add(track(1L, "notarialna", "Notarialna", TrackKind.EXAM, 10));
        tracks.add(track(2L, "komornicza", "Komornicza", TrackKind.EXAM, 20));
        tracks.add(track(9L, "student", "Student", TrackKind.STUDENT, 60));
        sources.add(sheet(1L, tracks.get(0), 2023));
        sources.add(sheet(2L, tracks.get(0), 2024));
        sources.add(sheet(3L, tracks.get(1), 2024));
        QuestionSource authored = new QuestionSource();
        authored.setId(4L);
        authored.setKind(SourceKind.AUTHORED);
        authored.setSlug("autorskie");
        authored.setName("Pytania autorskie");
        sources.add(authored);

        QuestionSourceRepository sourceRepository = InMemory.repo(QuestionSourceRepository.class, sources, QuestionSource::getId, QuestionSource::setId, (method, args) -> switch (method) {
            case "findAllSheetsWithTrack" -> sources.stream().filter(QuestionSource::isExamSheet)
                    .sorted(Comparator.<QuestionSource>comparingInt(s -> s.getTrack().getSortOrder())
                            .thenComparing(s -> s.getTrack().getId())
                            .thenComparing(QuestionSource::getExamYear, Comparator.reverseOrder())).toList();
            case "countBySlugValue" -> sources.stream().filter(s -> s.getSlug().equals(args[0])).count();
            case "countSheetsInYear" -> sources.stream().filter(s -> s.isExamSheet()
                    && s.getTrack().getId().equals(args[0]) && s.getExamYear().equals(args[1])).count();
            default -> throw new AssertionError("nieoczekiwane: " + method);
        });
        TrackRepository trackRepository = InMemory.repo(TrackRepository.class, tracks, Track::getId, Track::setId,
                (method, args) -> { throw new AssertionError("nieoczekiwane: " + method); });
        QuestionRepository questionRepository = FakeRepos.proxy(QuestionRepository.class, (method, args) -> switch (method) {
            case "totalsBySource" -> questionsBySource.entrySet().stream().map(e -> totals(e.getKey(), e.getValue()[0], e.getValue()[1])).toList();
            case "countInSource" -> questionsBySource.containsKey(args[0]) ? questionsBySource.get(args[0])[0] : 0L;
            default -> throw new AssertionError("nieoczekiwane: " + method);
        });
        service = new AdminSheetService(sourceRepository, trackRepository, questionRepository);
    }

    private static Track track(Long id, String slug, String shortName, TrackKind kind, int order) {
        Track track = new Track();
        track.setId(id);
        track.setSlug(slug);
        track.setName("Aplikacja " + shortName);
        track.setShortName(shortName);
        track.setKind(kind);
        track.setSortOrder(order);
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

    private static QuestionRepository.GroupTotals totals(Long id, long total, long hidden) {
        return new QuestionRepository.GroupTotals() {
            @Override public Long getGroupId() { return id; }
            @Override public Long getTotal() { return total; }
            @Override public Long getHiddenTotal() { return hidden; }
        };
    }

    @Test
    void list_showsOnlyExamSheets_newestYearFirst_withCounts_andCanFilterByApplication() {
        questionsBySource.put(2L, new long[]{150, 2});
        sources.get(0).setHidden(true);

        List<AdminSheetResponse> all = service.list(null);

        assertThat(all).extracting(AdminSheetResponse::id).containsExactly(2L, 1L, 3L);
        assertThat(all.get(0).questionCount()).isEqualTo(150);
        assertThat(all.get(0).hiddenQuestionCount()).isEqualTo(2);
        assertThat(all.get(1).hidden()).isTrue();
        assertThat(all.get(0).trackName()).isEqualTo("Notarialna");
        assertThat(service.list(2L)).extracting(AdminSheetResponse::id).containsExactly(3L);
    }

    @Test
    void create_namesTheSheetLikeTheSqlScripts() {
        AdminSheetResponse created = service.create(new AdminSheetCreateRequest(2L, 2025));

        assertThat(created.name()).isEqualTo("Komornicza 2025");
        assertThat(created.slug()).isEqualTo("komornicza-2025");
        assertThat(created.examYear()).isEqualTo(2025);
        assertThat(created.hidden()).isFalse();
        assertThat(created.questionCount()).isZero();
    }

    @Test
    void create_refusesADuplicateYear_theStudentTrack_andAnUnknownApplication() {
        assertThatThrownBy(() -> service.create(new AdminSheetCreateRequest(1L, 2024)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("2024");
        assertThatThrownBy(() -> service.create(new AdminSheetCreateRequest(9L, 2025)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(new AdminSheetCreateRequest(77L, 2025)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(sources).hasSize(4);
    }

    @Test
    void update_changingTheYearRenamesTheSheetAndMovesItsDerivedAddress() {
        AdminSheetResponse updated = service.update(2L, new AdminSheetUpdateRequest(2026, null));

        assertThat(updated.examYear()).isEqualTo(2026);
        assertThat(updated.name()).isEqualTo("Notarialna 2026");
        assertThat(updated.slug()).isEqualTo("notarialna-2026");
    }

    @Test
    void update_keepsAnAddressThatWasNotDerivedAndKeepsACustomName() {
        sources.get(1).setSlug("moj-adres");

        AdminSheetResponse updated = service.update(2L, new AdminSheetUpdateRequest(2026, " Notarialna  (wersja B) "));

        assertThat(updated.slug()).isEqualTo("moj-adres");
        assertThat(updated.name()).isEqualTo("Notarialna (wersja B)");
    }

    @Test
    void update_canRenameWithoutChangingTheYear_andRefusesAYearTakenByAnotherSheet() {
        assertThat(service.update(2L, new AdminSheetUpdateRequest(2024, "Inna nazwa")).name()).isEqualTo("Inna nazwa");
        assertThatThrownBy(() -> service.update(2L, new AdminSheetUpdateRequest(2023, null)))
                .isInstanceOf(ConflictException.class);
        assertThat(sources.get(1).getExamYear()).isEqualTo(2024);
    }

    @Test
    void sheetOperations_rejectTheAuthoredSourceAndMissingSheets() {
        assertThatThrownBy(() -> service.update(4L, new AdminSheetUpdateRequest(2024, null))).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.setHidden(4L, true)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.delete(4L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.delete(404L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void setHidden_hidesAndRestores() {
        assertThat(service.setHidden(1L, true).hidden()).isTrue();
        assertThat(sources.get(0).isHidden()).isTrue();
        assertThat(service.setHidden(1L, false).hidden()).isFalse();
    }

    @Test
    void delete_removesASheetWithoutQuestions_butNotOneThatHasThem() {
        questionsBySource.put(2L, new long[]{5, 5});

        service.delete(1L);
        assertThatThrownBy(() -> service.delete(2L)).isInstanceOf(ConflictException.class).hasMessageContaining("Ukryj");

        assertThat(sources).extracting(QuestionSource::getId).containsExactly(2L, 3L, 4L);
    }
}
