package pl.gradusiuris.backend.pool;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Przedmiot wybrany przez studenta (np. Prawo karne). Pulą pytań studenta są akty wybranych przedmiotów; brak wpisów
 * oznacza wszystkie przedmioty. Zwykłe numery zamiast relacji, bo tabela służy tylko do odczytu i zapisu listy numerów.
 */
@Entity
@Table(name = "user_courses")
@Getter
@Setter
@NoArgsConstructor
public class UserCourse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    public UserCourse(Long userId, Long courseId) {
        this.userId = userId;
        this.courseId = courseId;
    }
}
