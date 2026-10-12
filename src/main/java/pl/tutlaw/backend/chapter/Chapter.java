package pl.tutlaw.backend.chapter;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.tutlaw.backend.source.Source;

@Entity
@Table(name = "chapters")
@Getter
@Setter
@NoArgsConstructor
public class Chapter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_chapter_id")
    private Chapter parentChapter;

    @Column(nullable = false)
    private String title;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;
}