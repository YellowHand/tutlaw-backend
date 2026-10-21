package pl.gradusiuris.backend.track;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.gradusiuris.backend.legal.LegalAct;

import java.util.HashSet;
import java.util.Set;

/**
 * Ścieżka nauki: aplikacja (adwokacka i radcowska, sędziowska i prokuratorska, uzupełniająca sędziowska, notarialna,
 * komornicza) albo Student. Aplikacja ma własne arkusze egzaminacyjne ({@code QuestionSource}) i listę aktów prawnych
 * ustawianą przez administratora; pula użytkownika to pytania z jego arkuszy lub z tych aktów.
 */
@Entity
@Table(name = "tracks")
@Getter
@Setter
@NoArgsConstructor
public class Track {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String name;

    /** Krótka nazwa na etykiety i chipy, np. „Adwokacka i radcowska". */
    @Column(name = "short_name", nullable = false)
    private String shortName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TrackKind kind;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;

    /** Akty prawne aplikacji (dla ścieżki Student puste: tam akty biorą się z przedmiotów). */
    @ManyToMany
    @JoinTable(
            name = "track_acts",
            joinColumns = @JoinColumn(name = "track_id"),
            inverseJoinColumns = @JoinColumn(name = "legal_act_id")
    )
    private Set<LegalAct> acts = new HashSet<>();

    public boolean isStudent() {
        return kind == TrackKind.STUDENT;
    }
}
