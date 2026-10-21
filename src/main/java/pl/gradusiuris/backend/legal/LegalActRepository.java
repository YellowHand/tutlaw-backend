package pl.gradusiuris.backend.legal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface LegalActRepository extends JpaRepository<LegalAct, Long> {

    /** Akty o podanych numerach razem z dziedziną (jedno zapytanie, bez dociągania dziedziny osobno). */
    @Query("SELECT a FROM LegalAct a JOIN FETCH a.legalField WHERE a.id IN :ids")
    List<LegalAct> findAllWithFieldByIdIn(@Param("ids") Collection<Long> ids);

    /** Numery wszystkich widocznych (nieukrytych) aktów prawnych. */
    @Query("SELECT a.id FROM LegalAct a WHERE a.hidden = false")
    List<Long> findAllIds();

    @Query("SELECT a FROM LegalAct a JOIN FETCH a.legalField")
    List<LegalAct> findAllWithField();

    /** Ile aktów (także ukrytych) ma dany adres (slug); do wyboru wolnego adresu przy dodawaniu aktu. */
    @Query("SELECT COUNT(a) FROM LegalAct a WHERE a.slug = :slug")
    long countBySlugValue(@Param("slug") String slug);

    /** Numery wszystkich aktów podanych dziedzin. */
    @Query("SELECT a.id FROM LegalAct a WHERE a.hidden = false AND a.legalField.id IN :fieldIds")
    List<Long> findIdsByFieldIds(@Param("fieldIds") Collection<Long> fieldIds);
}
