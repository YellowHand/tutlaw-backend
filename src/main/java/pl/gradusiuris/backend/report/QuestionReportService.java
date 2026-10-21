package pl.gradusiuris.backend.report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gradusiuris.backend.common.exception.ResourceNotFoundException;
import pl.gradusiuris.backend.common.exception.TooManyRequestsException;
import pl.gradusiuris.backend.question.Question;
import pl.gradusiuris.backend.question.QuestionRepository;
import pl.gradusiuris.backend.report.dto.QuestionReportRequest;
import pl.gradusiuris.backend.report.dto.QuestionReportResponse;
import pl.gradusiuris.backend.user.CurrentUserResolver;
import pl.gradusiuris.backend.user.User;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/** Zgłaszanie błędów w pytaniach przez uczących się. Rozpatruje je administrator w panelu ({@code AdminReportService}). */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuestionReportService {

    private static final Duration DAILY_WINDOW = Duration.ofHours(24);
    /** Podpowiedź dla klienta, kiedy spróbować znowu (okno jest przesuwne, więc to tylko szacunek). */
    private static final long RETRY_AFTER_SECONDS = 3600;

    private final CurrentUserResolver currentUserResolver;
    private final QuestionRepository questionRepository;
    private final QuestionReportRepository reportRepository;
    private final Clock clock;

    /**
     * Zapisuje zgłoszenie. Ukryte pytanie traktujemy jak nieistniejące. Jeśli użytkownik ma już otwarte zgłoszenie tego pytania, uzupełniamy je
     * zamiast dodawać następne.
     */
    @Transactional
    public QuestionReportResponse report(Long questionId, QuestionReportRequest request, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);

        String problem = ReportRules.problem(request.reason(), request.comment());
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }

        Question question = questionRepository.findById(questionId)
                .filter(q -> !q.isHidden())
                .orElseThrow(() -> new ResourceNotFoundException("Nie znaleziono pytania"));

        String comment = ReportRules.normalizeComment(request.comment());
        Optional<QuestionReport> open = reportRepository.findByUserIdAndQuestionIdAndStatus(
                user.getId(), question.getId(), QuestionReportStatus.NEW);
        if (open.isPresent()) {
            QuestionReport existing = open.get();
            existing.setReason(request.reason());
            existing.setComment(comment);
            reportRepository.save(existing);
            return new QuestionReportResponse(existing.getId(), true);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (reportRepository.countByUserIdAndCreatedAtAfter(user.getId(), now.minus(DAILY_WINDOW)) >= ReportRules.DAILY_LIMIT) {
            throw new TooManyRequestsException(
                    "Wysłano dziś bardzo dużo zgłoszeń. Spróbuj ponownie później.", RETRY_AFTER_SECONDS);
        }

        QuestionReport report = new QuestionReport();
        report.setUser(user);
        report.setQuestion(question);
        report.setReason(request.reason());
        report.setComment(comment);
        report.setStatus(QuestionReportStatus.NEW);
        report.setCreatedAt(now);
        QuestionReport saved = reportRepository.save(report);
        log.info("Question report {} for question {} by user {}", saved.getId(), question.getId(), user.getId());
        return new QuestionReportResponse(saved.getId(), false);
    }
}
