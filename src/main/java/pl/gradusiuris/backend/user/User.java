package pl.gradusiuris.backend.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "private_email", nullable = false, unique = true)
    private String privateEmail;

    @Column(name = "university_email", nullable = false, unique = true)
    private String universityEmail;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "is_university_email_verified", nullable = false)
    private boolean universityEmailVerified = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.STUDENT;

    @Column(name = "is_active", nullable = false)
    private boolean active = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Kiedy zaakceptowano regulamin i politykę prywatności. Null: konto założone przed ich wprowadzeniem. */
    @Column(name = "terms_accepted_at")
    private LocalDateTime termsAcceptedAt;

    /**
     * Tokeny JWT wystawione przed tą chwilą (sekundy od 1970, jak claim iat) przestają działać. Ustawiane przy zmianie
     * i resecie hasła. Null: brak ograniczenia.
     */
    @Column(name = "tokens_valid_from")
    private Long tokensValidFrom;
}