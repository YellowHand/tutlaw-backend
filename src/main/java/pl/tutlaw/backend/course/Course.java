package pl.tutlaw.backend.course;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.tutlaw.backend.legal.LegalAct;

import java.util.HashSet;
import java.util.Set;

/**
 * Przedmiot studenta (np. Prawo karne, Prawoznawstwo, Paremie łacińskie). Przedmiot prawny ma listę aktów prawnych
 * z bazy pytań; przedmiot niestricte prawny (prawoznawstwo, historia prawa, informatyka prawnicza, paremie) ma jedno
 * zagadnienie ({@code LegalActKind.TOPIC}), do którego przypisane są jego pytania.
 */
@Entity
@Table(name = "courses")
@Getter
@Setter
@NoArgsConstructor
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;

    @ManyToMany
    @JoinTable(
            name = "course_acts",
            joinColumns = @JoinColumn(name = "course_id"),
            inverseJoinColumns = @JoinColumn(name = "legal_act_id")
    )
    private Set<LegalAct> acts = new HashSet<>();
}
