package pl.tutlaw.backend.category;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    /** Jak nazywamy elementy tej kategorii na ekranie, np. „Przedmioty" albo „Akty prawne". */
    @Column(name = "items_label", nullable = false)
    private String itemsLabel = "Przedmioty";

    /** Kolejność kategorii na liście (rosnąco). */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;
}