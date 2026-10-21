package pl.gradusiuris.backend.statistics.dto;

import java.util.List;

/**
 * Statystyki Testów (zakładka „Testy" na pulpicie).
 *
 * @param totalQuestions   ile pytań testowych użytkownik może teraz rozwiązywać
 * @param solvedQuestions  ile z nich rozwiązał co najmniej raz
 * @param answersLast30Days liczba odpowiedzi w testach z ostatnich 30 dni
 * @param correctLast30Days z tego poprawnych (niezależnie od „Nie jestem pewien")
 * @param accuracyRate     procent poprawnych z ostatnich 30 dni; {@code null}, gdy nie było odpowiedzi
 * @param confidentMistakesLast30Days błędne odpowiedzi bez „Nie jestem pewien" (utrwalone błędne przekonania)
 * @param luckyGuessesLast30Days poprawne odpowiedzi z „Nie jestem pewien" (wiedza krucha, wynik przypadkowy)
 * @param activity         odpowiedzi w testach na każdy z ostatnich 28 dni
 */
public record TestStatsResponse(
        int totalQuestions,
        int solvedQuestions,
        long answersLast30Days,
        long correctLast30Days,
        Double accuracyRate,
        long confidentMistakesLast30Days,
        long luckyGuessesLast30Days,
        List<ActivityHeatmapItem> activity
) {
}
