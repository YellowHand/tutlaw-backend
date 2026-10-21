package pl.tutlaw.backend.mark;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Oznaczenie jednego pytania (np. „pytanie o termin"). Zwykłe numery zamiast relacji, bo oznaczenia czyta się hurtem
 * (numery pytań z zatwierdzonym oznaczeniem) i nie ma po co ładować pytań ani użytkowników.
 */
@Entity
@Table(name = "question_marks")
@Getter
@Setter
@NoArgsConstructor
public class QuestionMark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "mark", nullable = false)
    private QuestionMarkType mark;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false)
    private MarkOrigin origin;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MarkStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Kiedy administrator zdecydował (null dla propozycji). */
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;
}
