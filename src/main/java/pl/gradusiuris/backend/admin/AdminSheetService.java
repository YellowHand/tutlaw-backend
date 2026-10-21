package pl.gradusiuris.backend.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gradusiuris.backend.admin.dto.AdminSheetCreateRequest;
import pl.gradusiuris.backend.admin.dto.AdminSheetResponse;
import pl.gradusiuris.backend.admin.dto.AdminSheetUpdateRequest;
import pl.gradusiuris.backend.common.exception.ConflictException;
import pl.gradusiuris.backend.common.exception.ResourceNotFoundException;
import pl.gradusiuris.backend.question.QuestionRepository;
import pl.gradusiuris.backend.source.QuestionSource;
import pl.gradusiuris.backend.source.QuestionSourceRepository;
import pl.gradusiuris.backend.source.SourceKind;
import pl.gradusiuris.backend.track.Track;
import pl.gradusiuris.backend.track.TrackKind;
import pl.gradusiuris.backend.track.TrackRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Arkusze egzaminacyjne w panelu administratora: jedna aplikacja w jednym roku. Dodawanie (nazwa i adres powstają z
 * aplikacji i roku, jak w skryptach wczytujących pytania), zmiana roku lub nazwy, ukrywanie i usuwanie (tylko arkusza bez
 * pytań). Źródło „Pytania autorskie" jest jedno i nie podlega zarządzaniu.
 */
@Service
@RequiredArgsConstructor
public class AdminSheetService {

    private static final int SLUG_MAX = 100;

    private final QuestionSourceRepository sourceRepository;
    private final TrackRepository trackRepository;
    private final QuestionRepository questionRepository;

    /** Arkusze wszystkich aplikacji (albo jednej, gdy podano {@code trackId}), także ukryte, razem z licznikami pytań. */
    @Transactional(readOnly = true)
    public List<AdminSheetResponse> list(Long trackId) {
        Map<Long, QuestionRepository.GroupTotals> totals = AdminMaps.totals(questionRepository.totalsBySource());
        List<AdminSheetResponse> result = new ArrayList<>();
        for (QuestionSource sheet : sourceRepository.findAllSheetsWithTrack()) {
            if (trackId == null || sheet.getTrack().getId().equals(trackId)) {
                result.add(describe(sheet, totals));
            }
        }
        return result;
    }

    @Transactional
    public AdminSheetResponse create(AdminSheetCreateRequest request) {
        Track track = trackRepository.findById(request.trackId())
                .orElseThrow(() -> new IllegalArgumentException("Nie ma takiej aplikacji."));
        if (track.getKind() != TrackKind.EXAM) {
            throw new IllegalArgumentException("Arkusze mają tylko aplikacje, nie ścieżka Student.");
        }
        int year = request.examYear();
        requireFreeYear(track.getId(), year);

        QuestionSource sheet = new QuestionSource();
        sheet.setKind(SourceKind.EXAM_SHEET);
        sheet.setTrack(track);
        sheet.setExamYear(year);
        sheet.setName(derivedName(track, year));
        sheet.setSlug(Slugs.unique(derivedSlug(track, year), SLUG_MAX, slug -> sourceRepository.countBySlugValue(slug) > 0));
        sheet.setHidden(false);
        sourceRepository.save(sheet);
        return get(sheet.getId());
    }

    /**
     * Zmienia rok i nazwę arkusza. Pusta nazwa oznacza nazwę wyprowadzoną z aplikacji i roku. Adres (slug) idzie za
     * rokiem tylko wtedy, gdy był wyprowadzony z aplikacji i dawnego roku, żeby nie rozjechać się ze skryptami SQL.
     */
    @Transactional
    public AdminSheetResponse update(Long id, AdminSheetUpdateRequest request) {
        QuestionSource sheet = findSheet(id);
        Track track = sheet.getTrack();
        int oldYear = sheet.getExamYear();
        int year = request.examYear();
        if (year != oldYear) {
            requireFreeYear(track.getId(), year);
        }
        String requested = request.name() == null ? "" : request.name().trim().replaceAll("\\s+", " ");
        sheet.setName(requested.isEmpty() ? derivedName(track, year) : requested);
        if (year != oldYear && sheet.getSlug().equals(derivedSlug(track, oldYear))) {
            sheet.setSlug(Slugs.unique(derivedSlug(track, year), SLUG_MAX, slug -> sourceRepository.countBySlugValue(slug) > 0));
        }
        sheet.setExamYear(year);
        sourceRepository.save(sheet);
        return get(id);
    }

    /** Ukrywa albo przywraca arkusz: ukryty znika wszystkim użytkownikom, a pytania i historia zostają. */
    @Transactional
    public AdminSheetResponse setHidden(Long id, boolean hidden) {
        QuestionSource sheet = findSheet(id);
        sheet.setHidden(hidden);
        sourceRepository.save(sheet);
        return get(id);
    }

    /** Usuwa arkusz tylko wtedy, gdy nie ma żadnych pytań (także ukrytych). Arkusz z pytaniami można tylko ukryć. */
    @Transactional
    public void delete(Long id) {
        QuestionSource sheet = findSheet(id);
        long questions = questionRepository.countInSource(id);
        if (questions > 0) {
            throw new ConflictException("Arkusz ma pytania (" + questions + "), więc nie można go usunąć. Ukryj go zamiast tego.");
        }
        sourceRepository.delete(sheet);
    }

    private AdminSheetResponse get(Long id) {
        Map<Long, QuestionRepository.GroupTotals> totals = AdminMaps.totals(questionRepository.totalsBySource());
        return sourceRepository.findAllSheetsWithTrack().stream()
                .filter(s -> s.getId().equals(id)).findFirst()
                .map(s -> describe(s, totals))
                .orElseThrow(() -> new ResourceNotFoundException("Nie ma takiego arkusza."));
    }

    private QuestionSource findSheet(Long id) {
        return sourceRepository.findById(id)
                .filter(QuestionSource::isExamSheet)
                .orElseThrow(() -> new ResourceNotFoundException("Nie ma takiego arkusza."));
    }

    private void requireFreeYear(Long trackId, int year) {
        if (sourceRepository.countSheetsInYear(trackId, year) > 0) {
            throw new ConflictException("Ta aplikacja ma już arkusz z roku " + year + ".");
        }
    }

    private static AdminSheetResponse describe(QuestionSource sheet, Map<Long, QuestionRepository.GroupTotals> totals) {
        return new AdminSheetResponse(
                sheet.getId(), sheet.getSlug(), sheet.getName(),
                sheet.getTrack().getId(), sheet.getTrack().getShortName(), sheet.getExamYear(), sheet.isHidden(),
                AdminMaps.total(totals, sheet.getId()), AdminMaps.hidden(totals, sheet.getId()));
    }

    /** Nazwa arkusza jak w skryptach SQL: krótka nazwa aplikacji i rok (np. „Notarialna 2024"). */
    static String derivedName(Track track, int year) {
        return track.getShortName() + " " + year;
    }

    /** Adres arkusza jak w skryptach SQL: adres aplikacji i rok (np. {@code notarialna-2024}). */
    static String derivedSlug(Track track, int year) {
        return track.getSlug() + "-" + year;
    }
}
