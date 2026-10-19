package pl.tutlaw.backend.pool;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserPoolActRepository extends JpaRepository<UserPoolAct, Long> {

    @Query("SELECT p.legalActId FROM UserPoolAct p WHERE p.userId = :userId ORDER BY p.legalActId")
    List<Long> findActIdsByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM UserPoolAct p WHERE p.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
