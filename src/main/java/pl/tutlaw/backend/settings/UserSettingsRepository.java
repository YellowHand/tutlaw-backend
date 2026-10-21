package pl.tutlaw.backend.settings;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.tutlaw.backend.common.GroupCount;

import java.util.List;

public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {

    /** Ilu użytkowników wybrało daną ścieżkę (groupId = numer ścieżki); panel administratora. */
    @Query("SELECT s.trackId AS groupId, COUNT(s) AS total FROM UserSettings s WHERE s.trackId IS NOT NULL GROUP BY s.trackId")
    List<GroupCount> countByTrack();

    /** Ilu użytkowników wybrało ścieżkę: aplikacja wybrana przez kogokolwiek nie może zostać usunięta. */
    @Query("SELECT COUNT(s) FROM UserSettings s WHERE s.trackId = :trackId")
    long countUsingTrack(@Param("trackId") Long trackId);
}
