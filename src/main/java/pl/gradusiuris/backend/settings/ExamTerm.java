package pl.gradusiuris.backend.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Termin najbliższego egzaminu jednej aplikacji (np. notarialnej). Ustawia go administrator; {@code null} w
 * {@code examDate} oznacza „termin nieogłoszony". Użytkownik wybiera ścieżkę, a data bierze się stąd.
 */
@Entity
@Table(name = "exam_terms")
@Getter
@Setter
@NoArgsConstructor
public class ExamTerm {

    /** Numer ścieżki-aplikacji. Zwykły numer zamiast relacji: wiersz służy tylko do odczytu daty. */
    @Id
    @Column(name = "track_id")
    private Long trackId;

    @Column(name = "exam_date")
    private LocalDate examDate;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public ExamTerm(Long trackId) {
        this.trackId = trackId;
    }
}
