package pl.gradusiuris.backend.exam;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.gradusiuris.backend.user.User;

import java.time.LocalDateTime;

/**
 * Podsumowanie jednego podejścia do egzaminu próbnego. Odpowiedzi na poszczególne pytania ({@link ExamAttemptAnswer})
 * zapisują się od poprawki 2 etapu 34; starsze podejścia mają {@link #hasAnswers} = false i zostają tylko z wynikiem.
 */
@Entity
@Table(name = "exam_attempts")
@Getter
@Setter
@NoArgsConstructor
public class ExamAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "finished_at", nullable = false)
    private LocalDateTime finishedAt;

    /** Nazwa zakresu w chwili egzaminu (np. "Prawo karne"), żeby historia była czytelna po zmianie nazw. */
    @Column(name = "scope_label", nullable = false, length = 300)
    private String scopeLabel;

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    @Column(name = "answered_count", nullable = false)
    private int answeredCount;

    @Column(name = "correct_count", nullable = false)
    private int correctCount;

    /**
     * Próg w procentach. Od stałego formatu egzaminu to tylko zaokrąglenie progu w punktach w górę (100 z 150 to 67%);
     * przy starszych podejściach to próg wybrany przez użytkownika.
     */
    @Column(name = "pass_threshold_percent", nullable = false)
    private int passThresholdPercent;

    /** Próg zaliczenia w punktach (100 przy pełnym egzaminie 150 pytań); null = podejście sprzed stałego formatu. */
    @Column(name = "pass_points")
    private Integer passPoints;

    @Column(nullable = false)
    private boolean passed;

    /** Limit czasu w minutach; null = egzamin bez limitu. */
    @Column(name = "time_limit_minutes")
    private Integer timeLimitMinutes;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    /** Czy egzamin został zakończony automatycznie, bo skończył się czas. */
    @Column(name = "timed_out", nullable = false)
    private boolean timedOut;

    /** Pytania z przesłanej listy, których nie dało się ocenić (usunięte albo niedostępne); nie wliczają się do wyniku. */
    @Column(name = "excluded_count", nullable = false)
    private int excludedCount;

    /** Czy odpowiedzi tego podejścia są zapisane, więc da się otworzyć jego raport. Starsze podejścia: nie. */
    @Column(name = "has_answers", nullable = false)
    private boolean hasAnswers;
}
