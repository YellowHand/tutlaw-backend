package pl.tutlaw.backend.source;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.tutlaw.backend.track.Track;

/**
 * Skąd pochodzą pytania: arkusz egzaminu wstępnego jednej ścieżki w jednym roku (np. „Notarialna 2023") albo pytania
 * autorskie. Arkusz „własny" użytkownika to arkusz jego ścieżki; arkusze pozostałych ścieżek są „z innych arkuszy".
 */
@Entity
@Table(name = "question_sources")
@Getter
@Setter
@NoArgsConstructor
public class QuestionSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private SourceKind kind;

    @Column(nullable = false)
    private String name;

    /** Ścieżka, której arkusz to jest (tylko EXAM_SHEET). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id")
    private Track track;

    /** Rok egzaminu (tylko EXAM_SHEET). */
    @Column(name = "exam_year")
    private Integer examYear;

    public boolean isExamSheet() {
        return kind == SourceKind.EXAM_SHEET;
    }
}
