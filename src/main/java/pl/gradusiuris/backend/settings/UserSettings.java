package pl.gradusiuris.backend.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @Column(name = "new_flashcards_per_day")
    private Integer newFlashcardsPerDay;

    @Column(name = "new_test_questions_per_day")
    private Integer newTestQuestionsPerDay;

    @Column(name = "max_reviews_per_day")
    private Integer maxReviewsPerDay;

    /** Docelowa szansa przypomnienia sobie karty w dniu powtórki (0,70–0,97). */
    @Column(name = "desired_retention")
    private Double desiredRetention;

    /**
     * Wybrana ścieżka: aplikacja, do której użytkownik się przygotowuje (data egzaminu bierze się z {@code ExamTerm}),
     * albo Student. {@code null}, gdy jeszcze nie wybrał (aplikacja pokazuje wtedy okno startowe). Zwykły numer
     * zamiast relacji: ścieżkę wczytuje się osobno, gdy jest potrzebna.
     */
    @Column(name = "track_id")
    private Long trackId;

    /** Wygląd aplikacji (domyślnie ciemny). Zapisany na koncie, żeby był taki sam na każdym urządzeniu. */
    @Enumerated(EnumType.STRING)
    @Column(name = "theme", nullable = false, length = 10)
    private Theme theme = Theme.DARK;

    /** Stałe ukrycie podpowiedzi o skrótach klawiszowych w testach (przycisk „×" przy podpowiedzi). */
    @Column(name = "hint_hidden_test", nullable = false)
    private boolean hintHiddenTest = false;

    /** To samo dla fiszek. */
    @Column(name = "hint_hidden_flashcard", nullable = false)
    private boolean hintHiddenFlashcard = false;

    /** Licznik czasu przy pytaniu jest ukryty (domyślnie widoczny). */
    @Column(name = "question_timer_hidden", nullable = false)
    private boolean questionTimerHidden = false;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public UserSettings(Long userId) {
        this.userId = userId;
    }
}
