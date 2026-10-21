package pl.gradusiuris.backend.review;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.gradusiuris.backend.question.CorrectOption;
import pl.gradusiuris.backend.question.Question;

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
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    /** Miejsce w sesji, od 1. */
    @Column(nullable = false)
    private int position;

    /**
     * Wybrana odpowiedź pod oryginalnym kluczem z bazy (odpowiedzi w teście są mieszane, więc to nie litera z ekranu).
     * Bez {@code length = 1}: Hibernate mapuje wtedy enum jako CHAR(1), a kolumna jest VARCHAR(1) (tak samo jak
     * {@code Question.correctOption}), więc walidacja schematu przy starcie by się nie powiodła.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "selected_option")
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
