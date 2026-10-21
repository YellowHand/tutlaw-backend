package pl.gradusiuris.backend.exam;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.gradusiuris.backend.question.CorrectOption;
import pl.gradusiuris.backend.question.Question;

/**
 * Odpowiedź na jedno pytanie w jednym podejściu do egzaminu. Zapisujemy tylko to, co wybrał użytkownik; treść pytania
 * i poprawną odpowiedź raport czyta z AKTUALNEGO pytania, więc po poprawce pytania w bazie raport pokazuje poprawioną treść.
 * Skasowanie podejścia kasuje jego odpowiedzi (baza: ON DELETE CASCADE).
 */
@Entity
@Table(name = "exam_attempt_answers")
@Getter
@Setter
@NoArgsConstructor
public class ExamAttemptAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    private ExamAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    /** Miejsce pytania w egzaminie, od 1 (tylko pytania, które dało się ocenić). */
    @Column(nullable = false)
    private int position;

    /**
     * Wybrana odpowiedź pod oryginalnym kluczem z bazy; {@code null} = bez odpowiedzi. Bez {@code length = 1}: Hibernate
     * mapuje wtedy enum jako VARCHAR (z {@code length = 1} oczekiwałby CHAR(1)), tak jak {@code Question.correctOption}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "selected_option")
    private CorrectOption selectedOption;
}
