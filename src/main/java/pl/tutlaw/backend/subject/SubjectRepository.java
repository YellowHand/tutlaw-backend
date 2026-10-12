package pl.tutlaw.backend.subject;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import pl.tutlaw.backend.common.GroupCount;

import java.util.List;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, Long> {
    boolean existsBySlug(String slug);

    List<Subject> findByCategoryId(Long categoryId);

    /** Liczba przedmiotów w każdej kategorii (groupId = id kategorii). */
    @Query("""
            SELECT s.category.id AS groupId, COUNT(s) AS total
            FROM Subject s
            GROUP BY s.category.id
            """)
    List<GroupCount> countByCategory();
}
