package pl.gradusiuris.backend.review.dto;

import pl.gradusiuris.backend.pool.SourceMode;
import pl.gradusiuris.backend.review.CustomTestKind;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Zapisany własny test na liście "Moje testy": konfiguracja z chwili układania i wynik do tej pory.
 *
 * @param finishedAt     kiedy odpowiedziano na ostatnie pytanie; {@code null} = test nieukończony
 * @param fieldIds       wybrane dziedziny (z nich, z {@code actIds}, {@code sectionIds} i {@code courseIds} da się ułożyć
 *                       test ponownie; wszystkie puste = cała pula)
 * @param courseIds      wybrane przedmioty studenta (ich akty są też w {@code actIds})
 * @param sectionIds     wybrane rozdziały aktów
 * @param source         źródło pytań wybrane przy układaniu testu (ALL, MINISTRY albo AUTHORED)
 * @param requestedCount o ile pytań prosił użytkownik
 * @param questionCount  ile pytań wylosowano
 * @param answeredCount  na ile już odpowiedział
 * @param guessedCount   w ilu zaznaczył "Nie jestem pewien"
 * @param timeSeconds    suma czasu odpowiadania (pojedyncza odpowiedź liczy się najwyżej 120 s)
 */
public record CustomTestSessionSummaryResponse(
        Long id,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        boolean finished,
        String scopeLabel,
        CustomTestKind kind,
        List<Long> fieldIds,
        List<Long> actIds,
        List<Long> courseIds,
        List<Long> sectionIds,
        SourceMode source,
        int requestedCount,
        int questionCount,
        int answeredCount,
        int correctCount,
        int guessedCount,
        double timeSeconds
) {
}
