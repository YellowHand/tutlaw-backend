package pl.gradusiuris.backend.flashcard;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Ręczne dodanie pytania do fiszek przez użytkownika (przycisk „Dodaj do fiszek" przy odpowiedzi w teście).
 * Dodane pytanie odblokowuje jego fiszkę w Fiszkach, tak samo jak pewny błąd. Zwykłe numery zamiast relacji,
 * bo tabela służy tylko do sprawdzania „czy odblokowane" i nie ma po co ładować użytkownika ani pytania.
 */
@Entity
@Table(name = "flashcard_additions")
@Getter
@Setter
@NoArgsConstructor
public class FlashcardAddition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "added_at", nullable = false, updatable = false)
    private LocalDateTime addedAt = LocalDateTime.now();
}
