package pl.gradusiuris.backend.question;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.gradusiuris.backend.legal.ActSection;
import pl.gradusiuris.backend.legal.LegalAct;
import pl.gradusiuris.backend.source.QuestionSource;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Pytanie ABC z jedną poprawną odpowiedzią. To jedyny rodzaj treści w aplikacji: fiszka nie jest osobnym rekordem,
 * tylko trybem nauki tego samego pytania (przód: treść pytania; tył: poprawna odpowiedź z wyjaśnieniem i podstawą
 * prawną). Stan nauki w obu trybach trzyma {@code UserQuestionState}.
 */
@Entity
@Table(name = "questions")
@Getter
@Setter
@NoArgsConstructor
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Akt prawny albo zagadnienie, do którego należy pytanie: to po nim pytania są współdzielone między ścieżkami. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "legal_act_id", nullable = false)
    private LegalAct legalAct;

    /**
     * Rozdział aktu, do którego należy pytanie (np. „XIX. Przestępstwa przeciwko życiu i zdrowiu"), albo {@code null}.
     * Rozdział zawsze należy do tego samego aktu co pytanie; pytanie bez rozdziału jest tylko w akcie jako całości.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private ActSection section;

    /** Arkusz egzaminacyjny albo pytania autorskie. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private QuestionSource source;

    /** Treść pytania (przód fiszki). */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String body;

    @Column(name = "option_a", columnDefinition = "TEXT", nullable = false)
    private String optionA;

    @Column(name = "option_b", columnDefinition = "TEXT", nullable = false)
    private String optionB;

    @Column(name = "option_c", columnDefinition = "TEXT", nullable = false)
    private String optionC;

    /** Bez {@code length}: Hibernate mapuje wtedy enum jako VARCHAR (z {@code length = 1} oczekiwałby CHAR(1)). */
    @Enumerated(EnumType.STRING)
    @Column(name = "correct_option", nullable = false)
    private CorrectOption correctOption;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    /** Podstawa prawna, np. "art. 415 § 1 k.c." Zwykle jest; może jej nie być (np. pytanie z historii prawa). */
    @Column(name = "legal_basis", length = 500)
    private String legalBasis;

    /** Na jaki dzień sprawdzono stan prawny pytania. Opcjonalne. */
    @Column(name = "valid_as_of")
    private LocalDate validAsOf;

    /**
     * Dzień, od którego treść pytania uwzględnia zmianę w prawie (dzień wejścia zmiany w życie albo późniejszy dzień
     * aktualizacji pytania). Od tego dnia pytanie wraca jako zaległe u każdego, kto odpowiadał na nie wcześniej
     * ({@code LawChangeService}), a przez rok trafia do kategorii „Zmiany w prawie". Data z przyszłości jeszcze nic
     * nie zmienia. Wczytywana skryptem SQL (katalog baza/).
     */
    @Column(name = "law_changed_at")
    private LocalDate lawChangedAt;

    /** Krótki opis zmiany pokazywany przy pytaniu (np. „nowelizacja art. 415 k.c.") albo {@code null}. Wymaga daty. */
    @Column(name = "law_change_note", length = 500)
    private String lawChangeNote;

    /**
     * Ukryte pytanie nie pojawia się nikomu (testy, fiszki, liczniki, egzamin), a historia odpowiedzi zostaje.
     */
    @Column(nullable = false)
    private boolean hidden = false;

    /** Ukryta fiszka: pytanie zostaje w testach, ale nie ma z niego fiszki. */
    @Column(name = "flashcard_hidden", nullable = false)
    private boolean flashcardHidden = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Tekst wybranej opcji (A, B albo C). */
    public String optionText(CorrectOption option) {
        return switch (option) {
            case A -> optionA;
            case B -> optionB;
            case C -> optionC;
        };
    }

    /** Tekst poprawnej odpowiedzi: tył fiszki. */
    public String correctOptionText() {
        return optionText(correctOption);
    }
}
