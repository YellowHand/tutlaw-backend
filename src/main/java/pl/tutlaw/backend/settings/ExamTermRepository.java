package pl.tutlaw.backend.settings;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.tutlaw.backend.card.QuestionOrigin;

public interface ExamTermRepository extends JpaRepository<ExamTerm, QuestionOrigin> {
}
