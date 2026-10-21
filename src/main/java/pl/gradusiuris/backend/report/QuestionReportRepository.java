package pl.gradusiuris.backend.report;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pl.gradusiuris.backend.common.GroupCount;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface QuestionReportRepository extends JpaRepository<QuestionReport, Long> {

    /** Otwarte (nierozpatrzone) zgłoszenie tego użytkownika dla tego pytania, jeśli już je wysłał. */
    Optional<QuestionReport> findByUserIdAndQuestionIdAndStatus(Long userId, Long questionId, QuestionReportStatus status);

    /** Ile zgłoszeń użytkownik wysłał od podanej chwili (do dziennego limitu). */
    long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime since);

    /** Ile jest zgłoszeń w danym stanie (np. nowych, do rozpatrzenia); przegląd w panelu administratora. */
    long countByStatus(QuestionReportStatus status);

    /** Zgłoszenia użytkownika wraz z treścią pytania, do eksportu jego danych. */
    @Query("""
            SELECT r.question.id AS questionId, r.question.body AS questionBody, r.reason AS reason, r.comment AS comment,
                   r.status AS status, r.createdAt AS createdAt
            FROM QuestionReport r
            WHERE r.user.id = :userId
            ORDER BY r.createdAt, r.id
            """)
    List<ExportedReport> findExportRows(@Param("userId") Long userId);

    // ------------------------------------------------------------------------------------------ panel administratora

    /**
     * Zgłoszenia w danym stanie (opcjonalnie tylko z jednym powodem) z pytaniem, od najnowszego. {@code reason} jest
     * obojętny, gdy {@code anyReason} jest prawdą (parametr pusty bywa w bazie kłopotliwy).
     */
    @Query(value = """
            SELECT r FROM QuestionReport r
            JOIN FETCH r.question q
            WHERE r.status = :status AND (:anyReason = true OR r.reason = :reason)
            ORDER BY r.createdAt DESC, r.id DESC
            """,
            countQuery = """
            SELECT COUNT(r) FROM QuestionReport r
            WHERE r.status = :status AND (:anyReason = true OR r.reason = :reason)
            """)
    Page<QuestionReport> findForAdmin(
            @Param("status") QuestionReportStatus status,
            @Param("anyReason") boolean anyReason,
            @Param("reason") QuestionReportReason reason,
            Pageable pageable);

    /** Ile otwartych zgłoszeń ma każde z podanych pytań (groupId = numer pytania). */
    @Query("""
            SELECT r.question.id AS groupId, COUNT(r) AS total
            FROM QuestionReport r
            WHERE r.question.id IN :questionIds AND r.status = pl.gradusiuris.backend.report.QuestionReportStatus.NEW
            GROUP BY r.question.id
            """)
    List<GroupCount> countOpenByQuestion(@Param("questionIds") Collection<Long> questionIds);

    /** Zmienia stan wszystkich otwartych zgłoszeń jednego pytania i zapisuje chwilę rozpatrzenia. Zwraca liczbę zmienionych. */
    @Modifying
    @Query("""
            UPDATE QuestionReport r
            SET r.status = :toStatus, r.resolvedAt = :now
            WHERE r.question.id = :questionId AND r.status = pl.gradusiuris.backend.report.QuestionReportStatus.NEW
            """)
    int resolveOpenForQuestion(
            @Param("questionId") Long questionId,
            @Param("toStatus") QuestionReportStatus to,
            @Param("now") LocalDateTime now);

    interface ExportedReport {
        Long getQuestionId();

        String getQuestionBody();

        QuestionReportReason getReason();

        String getComment();

        QuestionReportStatus getStatus();

        LocalDateTime getCreatedAt();
    }
}
