package pl.tutlaw.backend.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.tutlaw.backend.card.QuestionOrigin;

import java.time.LocalDateTime;

/**
 * Ustawienia jednego użytkownika. Pola liczbowe równe {@code null} oznaczają „użyj wartości domyślnej
 * z konfiguracji serwera", dzięki czemu zmiana domyślnych obejmuje wszystkich, którzy niczego nie ustawiali.
 * Wyjątkiem jest {@code newTestQuestionsPerDay}: {@code null} to limit automatyczny (idzie za planem do egzaminu).
 */
@Entity
@Table(name = "user_settings")
@Getter
@Setter
@NoArgsConstructor
public class UserSettings {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "new_cards_per_day")
    private Integer newCardsPerDay;

    @Column(name = "new_test_questions_per_day")
    private Integer newTestQuestionsPerDay;

    @Column(name = "max_reviews_per_day")
    private Integer maxReviewsPerDay;

    /** Docelowa szansa przypomnienia sobie karty w dniu powtórki (0,70–0,97). */
    @Column(name = "desired_retention")
    private Double desiredRetention;

    /** Aplikacja, do której użytkownik się przygotowuje (data egzaminu bierze się z {@code ExamTerm}); {@code null}, gdy nie wybrał. */
    @Enumerated(EnumType.STRING)
    @Column(name = "exam_origin", length = 40)
    private QuestionOrigin examOrigin;

    /** Wygląd aplikacji (domyślnie ciemny). Zapisany na koncie, żeby był taki sam na każdym urządzeniu. */
    @Enumerated(EnumType.STRING)
    @Column(name = "theme", nullable = false, length = 10)
    private Theme theme = Theme.DARK;

    /**
     * Czy użytkownik dokonał wyboru w oknie startowym: aplikacja ({@code examOrigin}) albo "Jestem studentem". Student
     * nie ma aplikacji, więc samo {@code examOrigin == null} nie wystarcza do rozpoznania, że wybór został zrobiony.
     */
    @Column(name = "start_choice_made", nullable = false)
    private boolean startChoiceMade = false;

    /** Stałe ukrycie podpowiedzi o skrótach klawiszowych w testach (przycisk „×" przy podpowiedzi). */
    @Column(name = "hint_hidden_test", nullable = false)
    private boolean hintHiddenTest = false;

    /** To samo dla fiszek. */
    @Column(name = "hint_hidden_flashcard", nullable = false)
    private boolean hintHiddenFlashcard = false;

    /** Licznik czasu przy pytaniu jest ukryty (domyślnie widoczny). */
    @Column(name = "question_timer_hidden", nullable = false)
    private boolean questionTimerHidden = false;

    /** Czy w testach mieszać kolejność odpowiedzi A/B/C (domyślnie tak). */
    @Column(name = "shuffle_options", nullable = false)
    private boolean shuffleOptions = true;

    /**
     * Filtr pochodzenia pytań w Testach: kody {@code QuestionOrigin} oddzielone przecinkami albo {@code null}
     * (wszystkie). Zapisywany i odczytywany przez {@code OriginFilterService}.
     */
    @Column(name = "test_origins", length = 200)
    private String testOrigins;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public UserSettings(Long userId) {
        this.userId = userId;
    }
}
