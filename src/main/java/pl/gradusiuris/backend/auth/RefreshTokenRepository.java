package pl.gradusiuris.backend.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Wiersz jest blokowany do końca transakcji, więc dwa równoległe odświeżenia tym samym tokenem (dwie karty)
     * wykonują się jedno po drugim: drugie widzi już wymieniony token i trafia w okno tolerancji.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Unieważnia wszystkie jeszcze obowiązujące tokeny jednej sesji (rodziny). */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.familyId = :familyId AND t.revokedAt IS NULL")
    int revokeFamily(@Param("familyId") String familyId, @Param("now") LocalDateTime now);

    /** Unieważnia wszystkie jeszcze obowiązujące tokeny konta (zmiana i reset hasła). */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.user.id = :userId AND t.revokedAt IS NULL")
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /** Sprzątanie: przy okazji wystawiania nowego tokenu kasujemy stare, dawno nieważne wiersze tego konta. */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM RefreshToken t WHERE t.user.id = :userId AND t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("userId") Long userId, @Param("cutoff") LocalDateTime cutoff);
}
