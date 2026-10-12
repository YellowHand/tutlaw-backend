package pl.tutlaw.backend.report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tutlaw.backend.card.Card;
import pl.tutlaw.backend.card.CardRepository;
import pl.tutlaw.backend.common.exception.ResourceNotFoundException;
import pl.tutlaw.backend.common.exception.TooManyRequestsException;
import pl.tutlaw.backend.report.dto.CardReportRequest;
import pl.tutlaw.backend.report.dto.CardReportResponse;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/** Zgłaszanie błędów w pytaniach przez uczących się. Rozpatrywanie zgłoszeń odbywa się poza aplikacją (SQL). */
@Service
@RequiredArgsConstructor
@Slf4j
public class CardReportService {

    private static final Duration DAILY_WINDOW = Duration.ofHours(24);
    /** Podpowiedź dla klienta, kiedy spróbować znowu (okno jest przesuwne, więc to tylko szacunek). */
    private static final long RETRY_AFTER_SECONDS = 3600;

    private final CurrentUserResolver currentUserResolver;
    private final CardRepository cardRepository;
    private final CardReportRepository reportRepository;
    private final Clock clock;

    /**
     * Zapisuje zgłoszenie. Pytanie musi być dostępne dla użytkownika (tak jak przy odpowiadaniu), a dla niedostępnego
     * odpowiadamy jak dla nieistniejącego. Jeśli użytkownik ma już otwarte zgłoszenie tego pytania, uzupełniamy je
     * zamiast dodawać następne.
     */
    @Transactional
    public CardReportResponse report(Long cardId, CardReportRequest request, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);

        String problem = ReportRules.problem(request.reason(), request.comment());
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }

        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Nie znaleziono pytania"));
        if (cardRepository.countReadableById(card.getId(), user.getId()) == 0) {
            throw new ResourceNotFoundException("Nie znaleziono pytania");
        }

        String comment = ReportRules.normalizeComment(request.comment());
        Optional<CardReport> open = reportRepository.findByUserIdAndCardIdAndStatus(
                user.getId(), card.getId(), CardReportStatus.NEW);
        if (open.isPresent()) {
            CardReport existing = open.get();
            existing.setReason(request.reason());
            existing.setComment(comment);
            reportRepository.save(existing);
            return new CardReportResponse(existing.getId(), true);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (reportRepository.countByUserIdAndCreatedAtAfter(user.getId(), now.minus(DAILY_WINDOW)) >= ReportRules.DAILY_LIMIT) {
            throw new TooManyRequestsException(
                    "Wysłano dziś bardzo dużo zgłoszeń. Spróbuj ponownie później.", RETRY_AFTER_SECONDS);
        }

        CardReport report = new CardReport();
        report.setUser(user);
        report.setCard(card);
        report.setReason(request.reason());
        report.setComment(comment);
        report.setStatus(CardReportStatus.NEW);
        report.setCreatedAt(now);
        CardReport saved = reportRepository.save(report);
        log.info("Card report {} for card {} by user {}", saved.getId(), card.getId(), user.getId());
        return new CardReportResponse(saved.getId(), false);
    }
}
