package pl.gradusiuris.backend.review;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.gradusiuris.backend.question.Question;
import pl.gradusiuris.backend.user.User;

import java.time.LocalDateTime;

/**
 * Stan nauki jednego pytania u jednego użytkownika w jednym trybie (FSRS). Test i fiszka tego samego pytania mają
 * osobne stany, czyli osobny harmonogram i osobny wskaźnik stabilności: pomyłka w teście nie przesuwa terminu fiszki
 * i odwrotnie. Brak stanu oznacza, że pytanie jest w tym trybie nowe.
 */
@Entity
@Table(name = "user_question_states")
@Getter
@Setter
@NoArgsConstructor
public class UserQuestionState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReviewMode mode;

    @Column(nullable = false)
    private double stability = 0;

    @Column(nullable = false)
    private double difficulty = 0;

    @Column(name = "due_date", nullable = false)
    private LocalDateTime dueDate = LocalDateTime.now();

    @Column(nullable = false)
    private int reps = 0;

    @Column(nullable = false)
    private int lapses = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LearningState state = LearningState.NEW;

    @Column(name = "last_review_at")
    private LocalDateTime lastReviewAt;

    @Column(name = "avg_time_seconds")
    private Double avgTimeSeconds;

    /** Zawieszone pytanie (w tym trybie) nie trafia do kolejek ani liczników powtórek, ale zachowuje cały postęp. */
    @Column(nullable = false)
    private boolean suspended = false;

    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    /**
     * Blokada optymistyczna: gdy dwie odpowiedzi na to samo pytanie (np. dwa okna) zmienią stan równocześnie,
     * druga dostaje konflikt zamiast po cichu nadpisać pierwszą.
     */
    @Version
    @Column(nullable = false)
    private long version;
}
