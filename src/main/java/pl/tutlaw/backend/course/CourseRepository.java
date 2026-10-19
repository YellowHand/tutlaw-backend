package pl.tutlaw.backend.course;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {

    List<Course> findAllByOrderBySortOrderAscIdAsc();

    /** Numery aktów wszystkich przedmiotów (baza ścieżki Student), bez powtórzeń. */
    @Query("SELECT DISTINCT a.id FROM Course c JOIN c.acts a")
    List<Long> findAllActIds();

    /** Pary (przedmiot, akt) do zbudowania katalogu przedmiotów studenta. */
    @Query("SELECT c.id AS courseId, a.id AS actId FROM Course c JOIN c.acts a")
    List<CourseAct> findCourseActs();

    interface CourseAct {
        Long getCourseId();

        Long getActId();
    }
}
