package pl.tutlaw.backend.course;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {

    List<Course> findAllByOrderBySortOrderAscIdAsc();

    /** Pary (przedmiot, akt) do zbudowania katalogu przedmiotów studenta. */
    @Query("SELECT c.id AS courseId, a.id AS actId FROM Course c JOIN c.acts a WHERE a.hidden = false")
    List<CourseAct> findCourseActs();

    /** Numery aktów podanych przedmiotów, bez powtórzeń (akty mogą należeć do kilku przedmiotów). */
    @Query("SELECT DISTINCT a.id FROM Course c JOIN c.acts a WHERE c.id IN :courseIds AND a.hidden = false")
    List<Long> findActIdsByCourseIds(@Param("courseIds") Collection<Long> courseIds);

    /** Pary (przedmiot, akt) także z ukrytymi aktami (widok administratora). */
    @Query("SELECT c.id AS courseId, a.id AS actId FROM Course c JOIN c.acts a")
    List<CourseAct> findAllCourseActs();

    interface CourseAct {
        Long getCourseId();

        Long getActId();
    }
}
