package pl.tutlaw.backend.review;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.tutlaw.backend.card.Card;
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

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "card_id", nullable = false)
    private Card card;

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
}