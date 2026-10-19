package pl.tutlaw.backend.review;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.tutlaw.backend.question.Question;
import pl.tutlaw.backend.user.User;

import java.time.LocalDateTime;

@Entity
@Table(name = "review_logs")
@Getter
@Setter
@NoArgsConstructor
public class ReviewLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(nullable = false)
    private int rating;

    @Column(name = "reviewed_at", nullable = false)
    private LocalDateTime reviewedAt = LocalDateTime.now();

    @Column(name = "elapsed_days")
    private Double elapsedDays;

    @Column(name = "stability_before")
    private Double stabilityBefore;

    @Column(name = "stability_after")
    private Double stabilityAfter;

    @Column(name = "time_spent_seconds")
    private Double timeSpentSeconds;

    /** Fiszka czy test. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReviewMode mode = ReviewMode.FLASHCARD;

    /** Tylko dla testu: czy wybrana opcja była poprawna (niezależnie od oceny pamięci). */
    @Column(name = "was_correct")
    private Boolean wasCorrect;

    /** Tylko dla testu: użytkownik zaznaczył „Nie jestem pewien". */
    @Column(name = "was_guessed", nullable = false)
    private boolean wasGuessed = false;
}
