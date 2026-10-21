package pl.tutlaw.backend.legal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Akt prawny (np. Kodeks cywilny) albo zagadnienie (np. Prawoznawstwo). To do niego przypisane jest każde pytanie,
 * dzięki czemu pytania mogą być współdzielone między ścieżkami: ścieżka ma listę aktów, a jej pula to pytania
 * z tych aktów (oraz z jej własnych arkuszy).
 */
@Entity
@Table(name = "legal_acts")
@Getter
@Setter
@NoArgsConstructor
public class LegalAct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "legal_field_id", nullable = false)
    private LegalField legalField;

    @Column(nullable = false)
    private String name;

    /** Skrót używany na kafelkach i w listach, np. „KC". Może być pusty. */
    @Column(name = "short_name")
    private String shortName;

    @Column(nullable = false, unique = true)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LegalActKind kind = LegalActKind.ACT;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;

    /**
     * Ukryty akt znika wszystkim użytkownikom (testy, fiszki, liczniki, egzamin), a jego pytania i historia odpowiedzi
     * zostają. Akt z pytaniami nie może zostać usunięty, tylko ukryty (panel administratora).
     */
    @Column(nullable = false)
    private boolean hidden = false;
}
