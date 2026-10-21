package pl.gradusiuris.backend.legal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Dziedzina prawa (np. „Prawo karne i proces karny"): duży dział, do którego należą akty prawne. Dziedziny widzą
 * wszyscy użytkownicy, ale aktów w nich tylko te, które należą do ich puli (zob. {@code PoolService}).
 */
@Entity
@Table(name = "legal_fields")
@Getter
@Setter
@NoArgsConstructor
public class LegalField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    /** Kolejność dziedzin na liście (rosnąco). */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;
}
