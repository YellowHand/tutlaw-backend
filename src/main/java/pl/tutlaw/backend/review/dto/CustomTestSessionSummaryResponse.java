package pl.tutlaw.backend.review.dto;

import pl.tutlaw.backend.card.QuestionOrigin;
import pl.tutlaw.backend.review.CustomTestKind;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Zapisany własny test na liście "Moje testy": konfiguracja z chwili układania i wynik do tej pory.
 *
 * @param finishedAt     kiedy odpowiedziano na ostatnie pytanie; {@code null} = test nieukończony
 * @param categoryIds    wybrane rodzaje (z nich i z {@code subjectIds} da się ułożyć test ponownie; oba puste = cały materiał)
 * @param origins        filtr pochodzenia pytań obowiązujący, gdy układano test
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
        List<Long> categoryIds,
        List<Long> subjectIds,
        Set<QuestionOrigin> origins,
        int requestedCount,
        int questionCount,
        int answeredCount,
        int correctCount,
        int guessedCount,
        double timeSeconds
) {
}
