package pl.tutlaw.backend.track;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TrackRepository extends JpaRepository<Track, Long> {

    Optional<Track> findBySlug(String slug);

    List<Track> findAllByOrderBySortOrderAscIdAsc();

    /** Numery aktów prawnych aplikacji (lista ustawiana przez administratora). */
    @Query("SELECT a.id FROM Track t JOIN t.acts a WHERE t.id = :trackId")
    List<Long> findActIds(@Param("trackId") Long trackId);
}
