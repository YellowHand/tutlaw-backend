package pl.tutlaw.backend.review;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.tutlaw.backend.user.User;

import java.time.LocalDateTime;

/**
 * Zapisany własny test: konfiguracja z chwili ułożenia (zakres, rodzaj pytań, liczba, filtr pochodzenia). Pytania i
 * odpowiedzi leżą w {@link CustomTestSessionQuestion}. Test bez {@code finishedAt} jest nieukończony.
 */
@Entity
@Table(name = "custom_test_sessions")
@Getter
@Setter
@NoArgsConstructor
public class CustomTestSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    /** Kiedy użytkownik odpowiedział na ostatnie pytanie. Null: test nieukończony. */
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CustomTestKind kind;

    @Column(name = "scope_label", nullable = false, length = 300)
    private String scopeLabel;

    /** Numery wybranych rodzajów, oddzielone przecinkami (puste: nie wybrano rodzajów). */
    @Column(name = "category_ids", nullable = false, length = 2000)
    private String categoryIds = "";

    /** Numery wybranych aktów prawnych, oddzielone przecinkami (puste: nie wybrano aktów). */
    @Column(name = "subject_ids", nullable = false, length = 2000)
    private String subjectIds = "";

    /** Filtr pochodzenia w chwili układania testu: kody oddzielone przecinkami albo null (wszystkie). */
    @Column(length = 200)
    private String origins;

    /** O ile pytań prosił użytkownik. */
    @Column(name = "requested_count", nullable = false)
    private int requestedCount;

    /** Ile pytań spełniało warunki w chwili układania (z nich losowano). */
    @Column(name = "available_count", nullable = false)
    private int availableCount;

    public boolean isFinished() {
        return finishedAt != null;
    }
}
