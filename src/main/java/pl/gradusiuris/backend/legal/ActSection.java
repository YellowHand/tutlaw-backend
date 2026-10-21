package pl.gradusiuris.backend.legal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Rozdział aktu prawnego (np. „XIX. Przestępstwa przeciwko życiu i zdrowiu" w Kodeksie karnym). Służy do wyboru zakresu
 * testu węższego niż cały akt. Pytanie ma co najwyżej jeden rozdział ({@code Question#section}); akt może nie mieć
 * rozdziałów wcale. Rozdziały wczytuje administrator skryptem SQL razem z przypisaniem pytań.
 */
@Entity
@Table(name = "act_sections")
@Getter
@Setter
@NoArgsConstructor
public class ActSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "legal_act_id", nullable = false)
    private LegalAct legalAct;

    /** Nazwa z numerem rozdziału, dokładnie tak, jak widzi ją użytkownik. */
    @Column(nullable = false, length = 300)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;
}
