package pl.tutlaw.backend.pool;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserCourseRepository extends JpaRepository<UserCourse, Long> {

    @Query("SELECT c.courseId FROM UserCourse c WHERE c.userId = :userId ORDER BY c.courseId")
    List<Long> findCourseIdsByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM UserCourse c WHERE c.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
