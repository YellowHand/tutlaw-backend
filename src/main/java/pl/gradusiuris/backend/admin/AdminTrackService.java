package pl.gradusiuris.backend.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gradusiuris.backend.admin.dto.AdminTrackRequest;
import pl.gradusiuris.backend.admin.dto.AdminTrackResponse;
import pl.gradusiuris.backend.common.exception.ConflictException;
import pl.gradusiuris.backend.common.exception.ResourceNotFoundException;
import pl.gradusiuris.backend.course.CourseRepository;
import pl.gradusiuris.backend.legal.LegalAct;
import pl.gradusiuris.backend.legal.LegalActRepository;
import pl.gradusiuris.backend.settings.ExamTerm;
import pl.gradusiuris.backend.settings.ExamTermRepository;
import pl.gradusiuris.backend.settings.UserSettingsRepository;
import pl.gradusiuris.backend.source.QuestionSourceRepository;
import pl.gradusiuris.backend.track.Track;
import pl.gradusiuris.backend.track.TrackKind;
import pl.gradusiuris.backend.track.TrackRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Aplikacje w panelu administratora: dodawanie, zmiana nazwy i kolejności, usuwanie (tylko pustej, której nikt nie
 * wybrał) oraz lista aktów prawnych aplikacji. Ścieżka Student jest jedna i nie da się jej usunąć; jej akty to akty
 * przedmiotów ({@link AdminCourseService}). Terminy egzaminów ustawia się istniejącym {@code PUT /api/exam-terms/{id}}.
 */
@Service
@RequiredArgsConstructor
public class AdminTrackService {

    static final int SORT_STEP = 10;
    private static final int SLUG_MAX = 60;

    private final TrackRepository trackRepository;
    private final LegalActRepository actRepository;
    private final CourseRepository courseRepository;
    private final QuestionSourceRepository sourceRepository;
    private final ExamTermRepository examTermRepository;
    private final UserSettingsRepository settingsRepository;

    /** Wszystkie ścieżki w kolejności z bazy: aplikacje i Student, z listą aktów, liczbą arkuszy i wyborów użytkowników. */
    @Transactional(readOnly = true)
    public List<AdminTrackResponse> list() {
        Map<Long, List<Long>> actsByTrack = new HashMap<>();
        for (TrackRepository.TrackAct pair : trackRepository.findAllTrackActs()) {
            actsByTrack.computeIfAbsent(pair.getTrackId(), k -> new ArrayList<>()).add(pair.getActId());
        }
        Set<Long> studentActs = new TreeSet<>();
        for (CourseRepository.CourseAct pair : courseRepository.findAllCourseActs()) {
            studentActs.add(pair.getActId());
        }
        Map<Long, Long> sheets = AdminMaps.counts(sourceRepository.countSheetsByTrack());
        Map<Long, Long> users = AdminMaps.counts(settingsRepository.countByTrack());
        Map<Long, LocalDate> dates = new HashMap<>();
        for (ExamTerm term : examTermRepository.findAll()) {
            if (term.getExamDate() != null) {
                dates.put(term.getTrackId(), term.getExamDate());
            }
        }

        List<AdminTrackResponse> result = new ArrayList<>();
        for (Track track : trackRepository.findAllByOrderBySortOrderAscIdAsc()) {
            List<Long> acts = track.isStudent()
                    ? List.copyOf(studentActs)
                    : actsByTrack.getOrDefault(track.getId(), List.of()).stream().sorted().toList();
            result.add(new AdminTrackResponse(
                    track.getId(), track.getSlug(), track.getName(), track.getShortName(), track.getKind(),
                    track.getSortOrder(), acts,
                    sheets.getOrDefault(track.getId(), 0L).intValue(),
                    users.getOrDefault(track.getId(), 0L),
                    track.isStudent() ? null : dates.get(track.getId())));
        }
        return result;
    }

    /** Nowa aplikacja (z egzaminem). Student jest jeden i istnieje od początku. */
    @Transactional
    public AdminTrackResponse create(AdminTrackRequest request) {
        String name = clean(request.name());
        String shortName = clean(request.shortName());
        List<Track> all = trackRepository.findAll();
        requireFreeNames(all, null, name, shortName);

        Track track = new Track();
        track.setName(name);
        track.setShortName(shortName);
        track.setKind(TrackKind.EXAM);
        track.setSlug(Slugs.unique(name, SLUG_MAX, slug -> all.stream().anyMatch(t -> t.getSlug().equals(slug))));
        track.setSortOrder(request.sortOrder() != null ? request.sortOrder()
                : all.stream().mapToInt(Track::getSortOrder).max().orElse(0) + SORT_STEP);
        trackRepository.save(track);
        examTermRepository.save(new ExamTerm(track.getId()));
        return describe(track.getId());
    }

    @Transactional
    public AdminTrackResponse update(Long id, AdminTrackRequest request) {
        Track track = find(id);
        String name = clean(request.name());
        String shortName = clean(request.shortName());
        requireFreeNames(trackRepository.findAll(), id, name, shortName);
        track.setName(name);
        track.setShortName(shortName);
        if (request.sortOrder() != null) {
            track.setSortOrder(request.sortOrder());
        }
        trackRepository.save(track);
        return describe(id);
    }

    /**
     * Usuwa aplikację tylko wtedy, gdy jest pusta: nie ma arkuszy (nawet ukrytych) i nikt jej nie wybrał. Inaczej
     * użytkownicy zostaliby bez ścieżki, a pytania bez źródła. Lista jej aktów i termin egzaminu znikają razem z nią.
     */
    @Transactional
    public void delete(Long id) {
        Track track = find(id);
        if (track.isStudent()) {
            throw new IllegalArgumentException("Ścieżki Student nie można usunąć.");
        }
        long sheets = sourceRepository.countSheetsOfTrack(id);
        if (sheets > 0) {
            throw new ConflictException("Aplikacja ma arkusze (" + sheets + "), więc nie można jej usunąć. "
                    + "Najpierw usuń puste arkusze albo przenieś pytania.");
        }
        long users = settingsRepository.countUsingTrack(id);
        if (users > 0) {
            throw new ConflictException("Tę aplikację wybrało " + users + " użytkowników, więc nie można jej usunąć.");
        }
        trackRepository.delete(track);
    }

    /** Zastępuje listę aktów aplikacji. Dla ścieżki Student akty biorą się z przedmiotów. */
    @Transactional
    public AdminTrackResponse setActs(Long id, List<Long> actIds) {
        Track track = find(id);
        if (track.isStudent()) {
            throw new IllegalArgumentException("Akty ścieżki Student ustawia się w przedmiotach.");
        }
        List<LegalAct> acts = resolveActs(actIds);
        track.getActs().clear();
        track.getActs().addAll(acts);
        trackRepository.save(track);
        return describe(id);
    }

    private AdminTrackResponse describe(Long id) {
        return list().stream().filter(t -> t.id().equals(id)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Nie ma takiej aplikacji."));
    }

    private Track find(Long id) {
        return trackRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Nie ma takiej aplikacji."));
    }

    /** Akty o podanych numerach (bez powtórzeń); numer, którego nie ma, to błąd żądania. */
    List<LegalAct> resolveActs(List<Long> actIds) {
        List<Long> distinct = actIds.stream().distinct().toList();
        List<LegalAct> acts = distinct.isEmpty() ? List.of() : actRepository.findAllById(distinct);
        if (acts.size() != distinct.size()) {
            throw new IllegalArgumentException("Na liście jest akt, którego nie ma w bazie. Odśwież stronę i spróbuj ponownie.");
        }
        return acts.stream().sorted(Comparator.comparing(LegalAct::getId)).toList();
    }

    private static void requireFreeNames(List<Track> all, Long selfId, String name, String shortName) {
        for (Track other : all) {
            if (other.getId().equals(selfId)) {
                continue;
            }
            if (Slugs.fold(other.getName()).equals(Slugs.fold(name))) {
                throw new ConflictException("Jest już aplikacja o takiej nazwie.");
            }
            if (Slugs.fold(other.getShortName()).equals(Slugs.fold(shortName))) {
                throw new ConflictException("Jest już aplikacja o takiej krótkiej nazwie.");
            }
        }
    }

    private static String clean(String text) {
        String trimmed = text == null ? "" : text.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Nazwa nie może być pusta.");
        }
        return trimmed;
    }
}
