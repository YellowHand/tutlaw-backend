package pl.tutlaw.backend.user;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByPrivateEmail(String privateEmail);
    boolean existsByPrivateEmail(String privateEmail);
    boolean existsByUniversityEmail(String universityEmail);

    // Wyszukiwanie bez rozróżniania wielkości liter: "Jan@Gmail.com" to to samo konto co "jan@gmail.com".
    // First...OrderById: konta sprzed normalizacji adresów mogły różnić się tylko wielkością liter.
    Optional<User> findFirstByPrivateEmailIgnoreCaseOrderByIdAsc(String privateEmail);
    boolean existsByPrivateEmailIgnoreCase(String privateEmail);
    boolean existsByUniversityEmailIgnoreCase(String universityEmail);
}
