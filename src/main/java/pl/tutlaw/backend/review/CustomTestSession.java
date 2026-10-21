package pl.tutlaw.backend.review;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.tutlaw.backend.user.User;

import java.time.LocalDateTime;

/**
 * Zapisany własny test: konfiguracja z chwili ułożenia (zakres, źródło, rodzaj pytań, liczba). Pytania i
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

    /** Numery wybranych dziedzin, oddzielone przecinkami (puste: nie wybrano dziedzin). */
    @Column(name = "field_ids", nullable = false, length = 2000)
    private String fieldIds = "";

    /** Numery wybranych aktów prawnych, oddzielone przecinkami (puste: nie wybrano aktów). */
    @Column(name = "act_ids", nullable = false, length = 2000)
    private String actIds = "";

    /** Numery wybranych rozdziałów aktów, oddzielone przecinkami (puste: nie wybrano rozdziałów). */
    @Column(name = "section_ids", nullable = false, length = 2000)
    private String sectionIds = "";

    /** Numery wybranych przedmiotów studenta, oddzielone przecinkami (puste: nie wybrano przedmiotów). */
    @Column(name = "course_ids", nullable = false, length = 500)
    private String courseIds = "";

    /**
     * Źródło pytań wybrane przy układaniu testu, zapisane jako kody grup (OWN_SHEETS, OTHER_SHEETS, AUTHORED) oddzielone
     * przecinkami albo null (wszystkie źródła). Zob. {@code SourceMode} i {@code SourceFilter#serialize}.
     */
    @Column(name = "source_groups", length = 100)
    private String sourceGroups;

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
