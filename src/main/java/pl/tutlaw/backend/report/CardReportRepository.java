package pl.tutlaw.backend.report;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CardReportRepository extends JpaRepository<CardReport, Long> {

    /** Otwarte (nierozpatrzone) zgłoszenie tego użytkownika dla tego pytania, jeśli już je wysłał. */
    Optional<CardReport> findByUserIdAndCardIdAndStatus(Long userId, Long cardId, CardReportStatus status);

    /** Ile zgłoszeń użytkownik wysłał od podanej chwili (do dziennego limitu). */
    long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime since);

    /** Zgłoszenia użytkownika wraz z treścią pytania, do eksportu jego danych. */
    @Query("""
            SELECT r.card.id AS cardId, r.card.front AS cardFront, r.reason AS reason, r.comment AS comment,
                   r.status AS status, r.createdAt AS createdAt
            FROM CardReport r
            WHERE r.user.id = :userId
            ORDER BY r.createdAt, r.id
            """)
    List<ExportedReport> findExportRows(@Param("userId") Long userId);

    interface ExportedReport {
        Long getCardId();

        String getCardFront();

        CardReportReason getReason();

        String getComment();

        CardReportStatus getStatus();

        LocalDateTime getCreatedAt();
    }
}
