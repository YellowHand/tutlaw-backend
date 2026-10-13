package pl.tutlaw.backend.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Jedyne, czego filtr JWT potrzebuje z bazy przy każdym żądaniu: czy konto istnieje i od kiedy tokeny są ważne.
     * Pusty wynik = brak konta. Wynik z {@code getTokensValidFrom() == null} = konto bez ograniczenia.
     * Zapytanie zwraca też id, żeby wiersz istniał zawsze, gdy istnieje konto, nawet przy pustym ograniczeniu
     * (pojedyncza kolumna z NULL mogłaby zostać zinterpretowana jako brak wyniku).
     * Projekcja zamiast encji, żeby nie ładować całego użytkownika przy każdym żądaniu.
     */
    @Query("SELECT u.id AS id, u.tokensValidFrom AS tokensValidFrom FROM User u WHERE u.privateEmail = :email")
    Optional<SessionInfo> findSessionInfoByPrivateEmail(@Param("email") String email);

    interface SessionInfo {
        Long getId();

        Long getTokensValidFrom();
    }
}
