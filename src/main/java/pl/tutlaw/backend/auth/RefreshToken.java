package pl.tutlaw.backend.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.tutlaw.backend.user.User;

import java.time.LocalDateTime;

/**
 * Token odświeżający jednej sesji. W bazie jest tylko skrót tokenu (patrz migracja V23). Tokeny jednego logowania
 * mają wspólny {@code familyId}: każde odświeżenie wystawia następny token tej samej rodziny.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "family_id", nullable = false, length = 36)
    private String familyId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** Kiedy ten token wymieniono na następny. Null: jeszcze nie użyty. */
    @Column(name = "rotated_at")
    private LocalDateTime rotatedAt;

    /** Kiedy unieważniono (wylogowanie, zmiana hasła, wykryte ponowne użycie). Null: obowiązuje. */
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;
}
