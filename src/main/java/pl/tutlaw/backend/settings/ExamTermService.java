package pl.tutlaw.backend.settings;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tutlaw.backend.settings.dto.ExamTermResponse;
import pl.tutlaw.backend.settings.dto.UpdateExamTermRequest;
import pl.tutlaw.backend.track.Track;
import pl.tutlaw.backend.track.TrackKind;
import pl.tutlaw.backend.track.TrackRepository;
import pl.tutlaw.backend.track.dto.TrackSummary;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Terminy egzaminów aplikacji. Odczyt dla każdego zalogowanego (lista do wyboru i podgląd), zapis tylko dla
 * administratora (pilnuje tego kontroler). Użytkownik nie ma własnej daty egzaminu. Student nie ma terminu.
 */
@Service
@RequiredArgsConstructor
public class ExamTermService {

    static final int MAX_EXAM_DISTANCE_YEARS = 3;

    private final ExamTermRepository repository;
    private final TrackRepository trackRepository;
    private final Clock clock;

    /** Wszystkie aplikacje z egzaminem w kolejności ścieżek, także te bez ustawionego terminu. */
    @Transactional(readOnly = true)
    public List<ExamTermResponse> list() {
        Map<Long, LocalDate> stored = new HashMap<>();
        for (ExamTerm term : repository.findAll()) {
            if (term.getExamDate() != null) {
                stored.put(term.getTrackId(), term.getExamDate());
            }
        }
        LocalDate today = LocalDate.now(clock);
        return trackRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .filter(track -> track.getKind() == TrackKind.EXAM)
                .map(track -> describe(track, stored.get(track.getId()), today))
                .toList();
    }

    /** Ustawia albo usuwa termin egzaminu aplikacji. Zwykła data w przeszłości jest odrzucana: nieaktualny termin się usuwa. */
    @Transactional
    public ExamTermResponse update(Long trackId, UpdateExamTermRequest request) {
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new IllegalArgumentException("Nie ma takiej ścieżki."));
        if (track.getKind() != TrackKind.EXAM) {
            throw new IllegalArgumentException("Termin można ustawić tylko dla aplikacji, nie dla ścieżki Student.");
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate date = request.examDate();
        if (date != null) {
            if (date.isBefore(today)) {
                throw new IllegalArgumentException("Termin egzaminu nie może być w przeszłości.");
            }
            if (date.isAfter(today.plusYears(MAX_EXAM_DISTANCE_YEARS))) {
                throw new IllegalArgumentException(
                        "Termin egzaminu jest zbyt odległy (najwyżej " + MAX_EXAM_DISTANCE_YEARS + " lata od dziś).");
            }
        }

        ExamTerm term = repository.findById(trackId).orElseGet(() -> new ExamTerm(trackId));
        term.setExamDate(date);
        term.setUpdatedAt(LocalDateTime.now(clock));
        repository.save(term);
        return describe(track, date, today);
    }

    private static ExamTermResponse describe(Track track, LocalDate date, LocalDate today) {
        Long days = null;
        if (date != null) {
            long between = ChronoUnit.DAYS.between(today, date);
            if (between >= 0) {
                days = between;
            }
        }
        return new ExamTermResponse(TrackSummary.of(track), date, days);
    }
}
