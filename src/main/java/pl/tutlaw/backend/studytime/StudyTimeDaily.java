package pl.tutlaw.backend.studytime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/** Czas nauki użytkownika w jednym dniu, w sekundach (patrz migracja V25). Zapisuje się go zapytaniem, nie tą encją. */
@Entity
@Table(name = "study_time_daily")
@IdClass(StudyTimeDaily.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class StudyTimeDaily {

    @Id
    @Column(name = "user_id")
    private Long userId;

    /** Dzień (kolumna {@code day}); w Javie {@code studyDay}, bo {@code day} jest słowem kluczowym w zapytaniach HQL. */
    @Id
    @Column(name = "day")
    private LocalDate studyDay;

    @Column(nullable = false)
    private int seconds;

    public static class Key implements Serializable {
        private Long userId;
        private LocalDate studyDay;

        public Key() {
        }

        public Key(Long userId, LocalDate studyDay) {
            this.userId = userId;
            this.studyDay = studyDay;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key other && Objects.equals(userId, other.userId) && Objects.equals(studyDay, other.studyDay);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, studyDay);
        }
    }
}
