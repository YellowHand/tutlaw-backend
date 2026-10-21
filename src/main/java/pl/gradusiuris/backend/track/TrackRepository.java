package pl.gradusiuris.backend.track;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TrackRepository extends JpaRepository<Track, Long> {

    Optional<Track> findBySlug(String slug);

    List<Track> findAllByOrderBySortOrderAscIdAsc();

    /** Numery aktów prawnych aplikacji (lista ustawiana przez administratora). */
    @Query("SELECT a.id FROM Track t JOIN t.acts a WHERE t.id = :trackId AND a.hidden = false")
    List<Long> findActIds(@Param("trackId") Long trackId);

    /** Wszystkie przypisania aktów do aplikacji, także ukrytych aktów (widok administratora). */
    @Query("SELECT t.id AS trackId, a.id AS actId FROM Track t JOIN t.acts a")
    List<TrackAct> findAllTrackActs();

    interface TrackAct {
        Long getTrackId();

        Long getActId();
    }
}
