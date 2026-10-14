package pl.tutlaw.backend.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.tutlaw.backend.card.QuestionOrigin;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Termin najbliższego egzaminu jednej aplikacji (np. notarialnej). Ustawia go administrator; {@code null} w
 * {@code examDate} oznacza „termin nieogłoszony". Użytkownik wybiera aplikację, a data bierze się stąd.
 */
@Entity
@Table(name = "exam_terms")
@Getter
@Setter
@NoArgsConstructor
public class ExamTerm {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "origin", length = 40)
    private QuestionOrigin origin;

    @Column(name = "exam_date")
    private LocalDate examDate;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public ExamTerm(QuestionOrigin origin) {
        this.origin = origin;
    }
}
