package pl.tutlaw.backend.pool;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tutlaw.backend.course.CourseRepository;
import pl.tutlaw.backend.legal.LegalActRepository;
import pl.tutlaw.backend.question.QuestionRepository;
import pl.tutlaw.backend.settings.UserSettings;
import pl.tutlaw.backend.settings.UserSettingsRepository;
import pl.tutlaw.backend.source.QuestionSourceRepository;
import pl.tutlaw.backend.source.SourceKind;
import pl.tutlaw.backend.track.Track;
import pl.tutlaw.backend.track.TrackRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Wylicza pulę pytań użytkownika. To jedyne miejsce z regułą dostępu do pytań:
 * <ul>
 *   <li><b>aplikacja</b>: pytania z jej własnych arkuszy ALBO z aktów prawnych z jej listy (ustawianej przez
 *       administratora); pytania autorskie i pytania z arkuszy innych aplikacji trafiają do puli przez wspólne akty;</li>
 *   <li><b>Student</b>: akty wybranych przez niego przedmiotów (bez wyboru: wszystkich przedmiotów); przedmiot to akty
 *       przypisane przez administratora, a student wybiera przedmioty, nie akty;</li>
 *   <li><b>zawężenie</b>: użytkownik aplikacji może zawęzić pulę do wybranych aktów (trwale, poza egzaminem
 *       próbnym); student zawęża przedmiotami;</li>
 *   <li><b>źródło</b>: własne arkusze / arkusze innych aplikacji / pytania autorskie; wybierane osobno przy każdym teście
 *       ({@link SourceMode}), a nie zapisane na koncie.</li>
 * </ul>
 * Użytkownik, który nie wybrał ścieżki, ma pulę obejmującą wszystko.
 */
@Service
@RequiredArgsConstructor
public class PoolService {

    private final UserSettingsRepository settingsRepository;
    private final TrackRepository trackRepository;
    private final CourseRepository courseRepository;
    private final LegalActRepository actRepository;
    private final QuestionSourceRepository sourceRepository;
    private final QuestionRepository questionRepository;
    private final UserPoolActRepository poolActRepository;
    private final UserCourseRepository userCourseRepository;

    /** Pula użytkownika ze wszystkich źródeł. Źródło wybiera się osobno przy każdym teście ({@link #forUser(Long, SourceFilter)}). */
    @Transactional(readOnly = true)
    public Pool forUser(Long userId) {
        return forUser(userId, SourceFilter.ALL);
    }

    /**
     * Pula użytkownika z pytaniami tylko z wybranych grup źródeł ({@code sourceIds} to źródła dozwolone przez filtr,
     * {@code allSourceIds} zostają wszystkie). Filtr nie jest już ustawieniem konta: wybiera się go przy układaniu testu
     * ({@link SourceMode}).
     */
    @Transactional(readOnly = true)
    public Pool forUser(Long userId, SourceFilter filter) {
        UserSettings settings = settingsRepository.findById(userId).orElse(null);
        Long trackId = settings == null ? null : settings.getTrackId();
        Track track = trackId == null ? null : trackRepository.findById(trackId).orElse(null);
        return build(userId, track, filter);
    }

    /** Pula jak {@link #forUser}, ale dla wskazanej ścieżki i filtru (np. do podglądu w oknie wyboru ścieżki). */
    @Transactional(readOnly = true)
    public Pool forTrack(Long userId, Track track, SourceFilter filter) {
        return build(userId, track, filter);
    }

    /**
     * Przedmioty studenta: akty każdego przedmiotu, który ma akty, oraz przedmioty w puli (wybrane przez studenta albo
     * wszystkie). Wybór, z którego nic nie zostało (przedmiot skasowany albo bez aktów), nie może wyciąć całej puli.
     */
    @Transactional(readOnly = true)
    public StudentCourses studentCourses(Long userId) {
        Map<Long, List<Long>> actsByCourse = new LinkedHashMap<>();
        for (CourseRepository.CourseAct pair : courseRepository.findCourseActs()) {
            actsByCourse.computeIfAbsent(pair.getCourseId(), k -> new ArrayList<>()).add(pair.getActId());
        }
        Set<Long> chosen = new LinkedHashSet<>();
        for (Long id : userCourseRepository.findCourseIdsByUserId(userId)) {
            if (actsByCourse.containsKey(id)) {
                chosen.add(id);
            }
        }
        boolean narrowed = !chosen.isEmpty() && !chosen.containsAll(actsByCourse.keySet());
        Set<Long> inPool = chosen.isEmpty() ? new LinkedHashSet<>(actsByCourse.keySet()) : chosen;
        return new StudentCourses(actsByCourse, inPool, narrowed);
    }

    /** Numery aktów ścieżki bez zawężenia użytkownika. Bez ścieżki: wszystkie akty. */
    @Transactional(readOnly = true)
    public Set<Long> baseActIds(Long userId, Track track) {
        return baseActIds(track, track != null && track.isStudent() ? studentCourses(userId) : null);
    }

    private Set<Long> baseActIds(Track track, StudentCourses studentCourses) {
        if (track == null) {
            return new LinkedHashSet<>(actRepository.findAllIds());
        }
        Set<Long> ids = new LinkedHashSet<>();
        if (track.isStudent()) {
            for (Long courseId : studentCourses.inPool()) {
                ids.addAll(studentCourses.actsByCourse().get(courseId));
            }
            return ids;
        }
        ids.addAll(trackRepository.findActIds(track.getId()));
        // Własne arkusze zawsze należą do puli, także gdy administrator nie wpisał ich aktu na listę ścieżki.
        List<Long> ownSheets = sourceRepository.findIdsByTrackId(track.getId());
        if (!ownSheets.isEmpty()) {
            ids.addAll(questionRepository.findActIdsInSources(ownSheets));
        }
        return ids;
    }

    private Pool build(Long userId, Track track, SourceFilter filter) {
        boolean studentTrack = track != null && track.isStudent();
        StudentCourses courses = studentTrack ? studentCourses(userId) : null;
        Set<Long> base = baseActIds(track, courses);

        // Student zawęża pulę przedmiotami (już w bazie); zawężenie do aktów dotyczy tylko ścieżek-aplikacji.
        List<Long> narrowing = new ArrayList<>();
        if (!studentTrack) {
            for (Long id : poolActRepository.findActIdsByUserId(userId)) {
                if (base.contains(id)) {
                    narrowing.add(id);
                }
            }
        }
        // Zawężenie, z którego po zmianie list przez administratora nic nie zostało, nie może wyciąć całej puli.
        boolean narrowed = studentTrack ? courses.narrowed() : !narrowing.isEmpty();
        Set<Long> effective = narrowing.isEmpty() ? base : new LinkedHashSet<>(narrowing);

        List<Long> ownSheets = track == null || track.isStudent()
                ? List.of() : sourceRepository.findIdsByTrackId(track.getId());
        Set<Long> ownSet = new LinkedHashSet<>(ownSheets);
        Set<Long> allSources = new LinkedHashSet<>();
        Set<Long> allowed = new LinkedHashSet<>();
        for (QuestionSourceRepository.SourceRow source : sourceRepository.findAllRows()) {
            allSources.add(source.getId());
            boolean ok;
            if (source.getKind() == SourceKind.AUTHORED) {
                ok = filter.authored();
            } else if (ownSet.contains(source.getId())) {
                ok = filter.ownSheets();
            } else {
                ok = filter.otherSheets();
            }
            if (ok) {
                allowed.add(source.getId());
            }
        }

        return new Pool(
                track == null ? null : track.getId(),
                track == null ? null : track.getKind(),
                Pool.orNoSuchId(base),
                Pool.orNoSuchId(effective),
                narrowed,
                filter,
                List.copyOf(ownSheets),
                Pool.orNoSuchId(allowed),
                Pool.orNoSuchId(allSources));
    }
}
