package pl.tutlaw.backend.settings;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tutlaw.backend.card.QuestionOrigin;
import pl.tutlaw.backend.settings.dto.ExamTermResponse;
import pl.tutlaw.backend.settings.dto.UpdateExamTermRequest;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Terminy egzaminów aplikacji. Odczyt dla każdego zalogowanego (lista do wyboru i podgląd), zapis tylko dla
 * administratora (pilnuje tego kontroler). Użytkownik nie ma już własnej daty egzaminu.
 */
@Service
@RequiredArgsConstructor
public class ExamTermService {

    static final int MAX_EXAM_DISTANCE_YEARS = 3;

    private final ExamTermRepository repository;
    private final Clock clock;

    /** Wszystkie aplikacje z egzaminem w kolejności wyliczenia, także te bez ustawionego terminu. */
    @Transactional(readOnly = true)
    public List<ExamTermResponse> list() {
        Map<QuestionOrigin, LocalDate> stored = new EnumMap<>(QuestionOrigin.class);
        for (ExamTerm term : repository.findAll()) {
            if (term.getExamDate() != null) {
                stored.put(term.getOrigin(), term.getExamDate());
            }
        }
        LocalDate today = LocalDate.now(clock);
        return java.util.Arrays.stream(QuestionOrigin.values())
                .filter(QuestionOrigin::isExamApplication)
                .map(origin -> describe(origin, stored.get(origin), today))
                .toList();
    }

    /** Ustawia albo usuwa termin egzaminu aplikacji. Zwykła data w przeszłości jest odrzucana: nieaktualny termin się usuwa. */
    @Transactional
    public ExamTermResponse update(QuestionOrigin origin, UpdateExamTermRequest request) {
        if (origin == null || !origin.isExamApplication()) {
            throw new IllegalArgumentException("Termin można ustawić tylko dla aplikacji, nie dla pytań autorskich.");
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

        ExamTerm term = repository.findById(origin).orElseGet(() -> new ExamTerm(origin));
        term.setExamDate(date);
        term.setUpdatedAt(LocalDateTime.now(clock));
        repository.save(term);
        return describe(origin, date, today);
    }

    private static ExamTermResponse describe(QuestionOrigin origin, LocalDate date, LocalDate today) {
        Long days = null;
        if (date != null) {
            long between = ChronoUnit.DAYS.between(today, date);
            if (between >= 0) {
                days = between;
            }
        }
        return new ExamTermResponse(origin, date, days);
    }
}
