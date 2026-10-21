package pl.gradusiuris.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** Ostatnio wysłany link użytkownika (do limitu częstotliwości wysyłania). */
    Optional<PasswordResetToken> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    /** Linki, których nikt jeszcze nie użył. Po zmianie hasła unieważniamy wszystkie. */
    List<PasswordResetToken> findByUserIdAndUsedAtIsNull(Long userId);
}
