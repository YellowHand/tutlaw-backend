package pl.gradusiuris.backend.source;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.gradusiuris.backend.common.GroupCount;

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

    /** Widoczne (nieukryte) źródła, od najnowszego roku. Ukryte arkusze nie istnieją dla użytkowników. */
    @Query("""
            SELECT s.id AS id, s.slug AS slug, s.kind AS kind, s.name AS name,
                   s.track.id AS trackId, s.examYear AS examYear
            FROM QuestionSource s
            WHERE s.hidden = false
            ORDER BY s.examYear DESC, s.id
            """)
    List<SourceRow> findAllRows();

    /** Numery widocznych źródeł podanego rodzaju. */
    @Query("SELECT s.id FROM QuestionSource s WHERE s.hidden = false AND s.kind = :kind")
    List<Long> findIdsByKind(@Param("kind") SourceKind kind);

    /** Numery widocznych arkuszy jednej ścieżki. */
    @Query("SELECT s.id FROM QuestionSource s WHERE s.hidden = false AND s.track.id = :trackId")
    List<Long> findIdsByTrackId(@Param("trackId") Long trackId);

    // ------------------------------------------------------------------------------------------ panel administratora

    /** Wszystkie arkusze egzaminacyjne (także ukryte) z aplikacją, w kolejności aplikacji i od najnowszego roku. */
    @Query("""
            SELECT s FROM QuestionSource s
            JOIN FETCH s.track t
            WHERE s.kind = pl.gradusiuris.backend.source.SourceKind.EXAM_SHEET
            ORDER BY t.sortOrder, t.id, s.examYear DESC, s.id
            """)
    List<QuestionSource> findAllSheetsWithTrack();

    /** Ile arkuszy (także ukrytych) ma każda aplikacja (groupId = numer aplikacji). */
    @Query("""
            SELECT s.track.id AS groupId, COUNT(s) AS total
            FROM QuestionSource s
            WHERE s.kind = pl.gradusiuris.backend.source.SourceKind.EXAM_SHEET
            GROUP BY s.track.id
            """)
    List<GroupCount> countSheetsByTrack();

    /** Ile arkuszy (także ukrytych) ma aplikacja: aplikacja z arkuszami nie może zostać usunięta. */
    @Query("SELECT COUNT(s) FROM QuestionSource s WHERE s.track.id = :trackId")
    long countSheetsOfTrack(@Param("trackId") Long trackId);

    /** Ile źródeł (także ukrytych) ma dany adres (slug). */
    @Query("SELECT COUNT(s) FROM QuestionSource s WHERE s.slug = :slug")
    long countBySlugValue(@Param("slug") String slug);

    /** Czy aplikacja ma już arkusz z tego roku (także ukryty). */
    @Query("SELECT COUNT(s) FROM QuestionSource s WHERE s.track.id = :trackId AND s.examYear = :year")
    long countSheetsInYear(@Param("trackId") Long trackId, @Param("year") Integer year);
}
