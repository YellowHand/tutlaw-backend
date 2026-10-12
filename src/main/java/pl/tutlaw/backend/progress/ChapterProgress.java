package pl.tutlaw.backend.progress;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.tutlaw.backend.chapter.Chapter;
import pl.tutlaw.backend.user.User;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "user_chapter_progress")
@IdClass(ChapterProgress.ChapterProgressId.class)
@Getter
@Setter
@NoArgsConstructor
public class ChapterProgress {

    @Id
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Id
    @ManyToOne
    @JoinColumn(name = "chapter_id")
    private Chapter chapter;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ChapterProgressId implements Serializable {
        private Long user;
        private Long chapter;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ChapterProgressId that)) return false;
            return Objects.equals(user, that.user) && Objects.equals(chapter, that.chapter);
        }

        @Override
        public int hashCode() {
            return Objects.hash(user, chapter);
        }
    }
}