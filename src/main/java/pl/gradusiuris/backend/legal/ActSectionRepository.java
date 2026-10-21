package pl.gradusiuris.backend.legal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.gradusiuris.backend.common.GroupCount;

import java.util.Collection;
import java.util.List;

public interface ActSectionRepository extends JpaRepository<ActSection, Long> {

    /** Rozdziały podanych aktów (numer aktu z rozdziałem: do składania drzewa wyboru). */
    @Query("""
            SELECT s.id AS id, s.legalAct.id AS legalActId, s.name AS name, s.sortOrder AS sortOrder
            FROM ActSection s
            WHERE s.legalAct.id IN :actIds
            """)
    List<SectionRow> findRowsByActIds(@Param("actIds") Collection<Long> actIds);

    /** Rozdziały jednego aktu w kolejności wyświetlania (panel administratora). */
    @Query("SELECT s FROM ActSection s WHERE s.legalAct.id = :actId ORDER BY s.sortOrder, s.id")
    List<ActSection> findByActOrdered(@Param("actId") Long actId);

    /** Ile rozdziałów ma każdy akt (groupId = numer aktu). */
    @Query("SELECT s.legalAct.id AS groupId, COUNT(s) AS total FROM ActSection s GROUP BY s.legalAct.id")
    List<GroupCount> countByAct();

    /** Odchudzony widok rozdziału do składania katalogu. */
    interface SectionRow {

        Long getId();

        Long getLegalActId();

        String getName();

        Integer getSortOrder();
    }
}
