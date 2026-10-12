package pl.tutlaw.backend.subject;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, Long> {
    boolean existsBySlug(String slug);

    List<Subject> findByCategoryId(Long categoryId);
}
