package pl.tutlaw.backend.source;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuestionSourceRepository extends JpaRepository<QuestionSource, Long> {

    /** Odchudzony widok źródła: bez ładowania ścieżki (numer ścieżki z klucza obcego). */
    interface SourceRow {
        Long getId();

        String getSlug();

        SourceKind getKind();

        String getName();

        Long getTrackId();

        Integer getExamYear();
    }

    @Query("""
            SELECT s.id AS id, s.slug AS slug, s.kind AS kind, s.name AS name,
                   s.track.id AS trackId, s.examYear AS examYear
            FROM QuestionSource s
            ORDER BY s.examYear DESC, s.id
            """)
    List<SourceRow> findAllRows();

    /** Numery źródeł podanego rodzaju. */
    @Query("SELECT s.id FROM QuestionSource s WHERE s.kind = :kind")
    List<Long> findIdsByKind(@Param("kind") SourceKind kind);

    /** Numery arkuszy jednej ścieżki. */
    @Query("SELECT s.id FROM QuestionSource s WHERE s.track.id = :trackId")
    List<Long> findIdsByTrackId(@Param("trackId") Long trackId);
}
