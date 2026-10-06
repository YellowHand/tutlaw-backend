package pl.tutlaw.backend.user;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByPrivateEmail(String privateEmail);
    boolean existsByPrivateEmail(String privateEmail);
    boolean existsByUniversityEmail(String universityEmail);
}