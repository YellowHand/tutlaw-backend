package pl.tutlaw.backend.card;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.tutlaw.backend.chapter.Chapter;
import pl.tutlaw.backend.source.Source;
import pl.tutlaw.backend.tag.Tag;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "cards")
@Getter
@Setter
@NoArgsConstructor
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "chapter_id")
    private Chapter chapter;

    @ManyToOne
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardType type;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String front;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String back;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "is_verbatim_quote", nullable = false)
    private boolean verbatimQuote = false;

    @Column(name = "option_a", columnDefinition = "TEXT")
    private String optionA;

    @Column(name = "option_b", columnDefinition = "TEXT")
    private String optionB;

    @Column(name = "option_c", columnDefinition = "TEXT")
    private String optionC;

    @Enumerated(EnumType.STRING)
    @Column(name = "correct_option")
    private CorrectOption correctOption;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    /** Podstawa prawna, np. "art. 415 k.c." Opcjonalna; pokazywana uczącemu się przy odpowiedzi. */
    @Column(name = "legal_basis", columnDefinition = "TEXT")
    private String legalBasis;

    /** Na jaki dzień sprawdzono stan prawny pytania. Opcjonalne. */
    @Column(name = "valid_as_of")
    private LocalDate validAsOf;

    @ManyToMany
    @JoinTable(
            name = "card_tags",
            joinColumns = @JoinColumn(name = "card_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tag> tags = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}