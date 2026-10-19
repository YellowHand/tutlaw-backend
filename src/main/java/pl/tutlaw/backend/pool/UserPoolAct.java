package pl.tutlaw.backend.pool;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Zawężenie puli użytkownika do wybranego aktu prawnego. Brak wpisów oznacza całą pulę ścieżki. Zwykłe numery zamiast
 * relacji, bo tabela służy tylko do odczytu i zapisu listy numerów.
 */
@Entity
@Table(name = "user_pool_acts")
@Getter
@Setter
@NoArgsConstructor
public class UserPoolAct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "legal_act_id", nullable = false)
    private Long legalActId;

    public UserPoolAct(Long userId, Long legalActId) {
        this.userId = userId;
        this.legalActId = legalActId;
    }
}
