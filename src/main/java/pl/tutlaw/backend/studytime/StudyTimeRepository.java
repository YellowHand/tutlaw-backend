package pl.tutlaw.backend.studytime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface StudyTimeRepository extends JpaRepository<StudyTimeDaily, StudyTimeDaily.Key> {

    /**
     * Dolicza sekundy do dnia (jedno atomowe zapytanie, więc równoległe zgłoszenia z dwóch kart się nie gubią).
     * Dzień nie może mieć więcej niż 24 godzin.
     */
    @Modifying
    @Query(value = """
            INSERT INTO study_time_daily (user_id, day, seconds)
            VALUES (:userId, :day, :seconds)
            ON CONFLICT (user_id, day)
            DO UPDATE SET seconds = LEAST(study_time_daily.seconds + EXCLUDED.seconds, 86400)
            """, nativeQuery = true)
    int addSeconds(@Param("userId") Long userId, @Param("day") LocalDate day, @Param("seconds") int seconds);

    /** Suma sekund od podanego dnia włącznie. */
    @Query("SELECT COALESCE(SUM(d.seconds), 0L) FROM StudyTimeDaily d WHERE d.userId = :userId AND d.studyDay >= :sinceDay")
    long sumSince(@Param("userId") Long userId, @Param("sinceDay") LocalDate sinceDay);

    @Query("SELECT COALESCE(SUM(d.seconds), 0L) FROM StudyTimeDaily d WHERE d.userId = :userId")
    long sumAll(@Param("userId") Long userId);

    /** Wszystkie dni użytkownika, od najnowszego (do eksportu danych). */
    @Query("SELECT d FROM StudyTimeDaily d WHERE d.userId = :userId ORDER BY d.studyDay DESC")
    List<StudyTimeDaily> findAllForUser(@Param("userId") Long userId);
}
