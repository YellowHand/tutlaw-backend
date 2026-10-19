package pl.tutlaw.backend.report;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface QuestionReportRepository extends JpaRepository<QuestionReport, Long> {

    /** Otwarte (nierozpatrzone) zgłoszenie tego użytkownika dla tego pytania, jeśli już je wysłał. */
    Optional<QuestionReport> findByUserIdAndQuestionIdAndStatus(Long userId, Long questionId, QuestionReportStatus status);

    /** Ile zgłoszeń użytkownik wysłał od podanej chwili (do dziennego limitu). */
    long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime since);

    /** Zgłoszenia użytkownika wraz z treścią pytania, do eksportu jego danych. */
    @Query("""
            SELECT r.question.id AS questionId, r.question.body AS questionBody, r.reason AS reason, r.comment AS comment,
                   r.status AS status, r.createdAt AS createdAt
            FROM QuestionReport r
            WHERE r.user.id = :userId
            ORDER BY r.createdAt, r.id
            """)
    List<ExportedReport> findExportRows(@Param("userId") Long userId);

    interface ExportedReport {
        Long getQuestionId();

        String getQuestionBody();

        QuestionReportReason getReason();

        String getComment();

        QuestionReportStatus getStatus();

        LocalDateTime getCreatedAt();
    }
}
