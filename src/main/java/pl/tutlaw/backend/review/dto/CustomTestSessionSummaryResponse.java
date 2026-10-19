package pl.tutlaw.backend.review.dto;

import pl.tutlaw.backend.review.CustomTestKind;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Zapisany własny test na liście "Moje testy": konfiguracja z chwili układania i wynik do tej pory.
 *
 * @param finishedAt     kiedy odpowiedziano na ostatnie pytanie; {@code null} = test nieukończony
 * @param fieldIds       wybrane dziedziny (z nich i z {@code actIds} da się ułożyć test ponownie; oba puste = cała pula)
 * @param sourceGroups   grupy źródeł obowiązujące, gdy układano test (OWN_SHEETS, OTHER_SHEETS, AUTHORED); wszystkie
 *                       trzy, gdy filtr był domyślny
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
        List<String> sourceGroups,
        int requestedCount,
        int questionCount,
        int answeredCount,
        int correctCount,
        int guessedCount,
        double timeSeconds
) {
}
