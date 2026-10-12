package pl.tutlaw.backend.review;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.tutlaw.backend.card.Card;
import pl.tutlaw.backend.user.User;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "user_card_state")
@IdClass(UserCardState.UserCardStateId.class)
@Getter
@Setter
@NoArgsConstructor
public class UserCardState {

    @Id
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Id
    @ManyToOne
    @JoinColumn(name = "card_id")
    private Card card;

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
    @Column(nullable = false)
    private CardLearningState state = CardLearningState.NEW;

    @Column(name = "last_review_at")
    private LocalDateTime lastReviewAt;

    @Column(name = "avg_time_seconds")
    private Double avgTimeSeconds;

    /** Zawieszona karta nie trafia do kolejek ani liczników powtórek, ale zachowuje cały postęp. */
    @Column(nullable = false)
    private boolean suspended = false;

    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    /**
     * Blokada optymistyczna: gdy dwie odpowiedzi na tę samą kartę (np. dwa okna) zmienią stan równocześnie,
     * druga dostaje konflikt zamiast po cichu nadpisać pierwszą.
     */
    @Version
    @Column(nullable = false)
    private long version;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class UserCardStateId implements Serializable {
        private Long user;
        private Long card;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof UserCardStateId that)) return false;
            return Objects.equals(user, that.user) && Objects.equals(card, that.card);
        }

        @Override
        public int hashCode() {
            return Objects.hash(user, card);
        }
    }
}