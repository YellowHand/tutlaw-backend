package pl.tutlaw.backend.statistics.dto;

/**
 * "Podsumowanie nauki": luźne liczby o całej dotychczasowej nauce użytkownika.
 *
 * @param totalAnswers            wszystkie odpowiedzi: w testach i oceny fiszek
 * @param testAnswers             odpowiedzi w testach (powtórki, błędy, własne testy)
 * @param flashcardReviews        oceny fiszek
 * @param examsDone               ukończone egzaminy próbne
 * @param customTestsDone         ukończone własne testy (osobno od egzaminów)
 * @param studySecondsToday       czas nauki dzisiaj w sekundach (czas w aplikacji, razem z czytaniem wyjaśnień)
 * @param studySecondsLast7Days   czas nauki w ostatnich 7 dniach (dziś i sześć poprzednich dni)
 * @param studySecondsTotal       czas nauki łącznie
 * @param averageSecondsPerQuestion średni czas odpowiedzi na pytanie w teście w sekundach (pojedyncza odpowiedź najwyżej
 *                                120 s); {@code null}, gdy nie ma pomiarów. To miara osobna od czasu nauki
 * @param averageSecondsPerFlashcard to samo dla fiszek
 */
public record LearningSummaryResponse(
        long totalAnswers,
        long testAnswers,
        long flashcardReviews,
        long examsDone,
        long customTestsDone,
        long studySecondsToday,
        long studySecondsLast7Days,
        long studySecondsTotal,
        Double averageSecondsPerQuestion,
        Double averageSecondsPerFlashcard
) {
}
