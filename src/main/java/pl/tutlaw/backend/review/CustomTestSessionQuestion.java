package pl.tutlaw.backend.review;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.tutlaw.backend.card.Card;
import pl.tutlaw.backend.card.CorrectOption;

import java.time.LocalDateTime;

/** Jedno pytanie zapisanego własnego testu i odpowiedź na nie (pusta, dopóki użytkownik nie odpowiedział). */
@Entity
@Table(name = "custom_test_session_questions")
@Getter
@Setter
@NoArgsConstructor
public class CustomTestSessionQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private CustomTestSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id", nullable = false)
    private Card card;

    /** Miejsce w sesji, od 1. */
    @Column(nullable = false)
    private int position;

    /** Wybrana odpowiedź pod oryginalnym kluczem z bazy (odpowiedzi w teście są mieszane, więc to nie litera z ekranu). */
    @Enumerated(EnumType.STRING)
    @Column(name = "selected_option", length = 1)
    private CorrectOption selectedOption;

    @Column(name = "was_correct")
    private Boolean wasCorrect;

    @Column(name = "was_guessed", nullable = false)
    private boolean wasGuessed = false;

    @Column(name = "time_spent_seconds")
    private Double timeSpentSeconds;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    public boolean isAnswered() {
        return answeredAt != null;
    }
}
