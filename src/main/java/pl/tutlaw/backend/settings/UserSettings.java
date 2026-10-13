package pl.tutlaw.backend.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Ustawienia nauki jednego użytkownika. Pola liczbowe równe {@code null} oznaczają „użyj wartości domyślnej
 * z konfiguracji serwera", dzięki czemu zmiana domyślnych obejmuje wszystkich, którzy niczego nie ustawiali.
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

    @Column(name = "exam_date")
    private LocalDate examDate;

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
