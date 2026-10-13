package pl.tutlaw.backend.statistics.dto;

import pl.tutlaw.backend.card.QuestionOrigin;

import java.util.List;

/**
 * Statystyki Testów według pochodzenia pytań (zakładka „Statystyki"). Zawsze wszystkie sześć pochodzeń w kolejności
 * enumu, także te bez pytań (same zera). Nie podlega filtrowi pochodzenia z Testów.
 *
 * @param origins pochodzenia: autorskie i pięć aplikacji
 */
public record OriginStatsResponse(List<OriginEntry> origins) {

    /**
     * @param origin pochodzenie
     * @param stats  suma ze wszystkich lat
     * @param years  rozbicie na lata egzaminu, od najnowszego; puste dla pytań autorskich (nie mają roku)
     */
    public record OriginEntry(QuestionOrigin origin, Metrics stats, List<YearEntry> years) {
    }

    public record YearEntry(int year, Metrics stats) {
    }

    /**
     * Liczby dotyczą pytań dostępnych teraz dla użytkownika (nieukrytych, z puli płaskiej albo z przeczytanego rozdziału).
     *
     * @param totalQuestions            ile jest takich pytań
     * @param solvedQuestions           ile z nich rozwiązano co najmniej raz
     * @param matureQuestions           ile jest utrwalonych (stabilność powyżej progu, jak „Opanowane" w statystykach)
     * @param dueQuestions              ile ma już miniony termin powtórki (bez zawieszonych)
     * @param answers                   wszystkie odpowiedzi w testach
     * @param correctAnswers            z tego poprawne (niezależnie od „Nie jestem pewien")
     * @param accuracyRate              procent poprawnych ze wszystkich odpowiedzi; {@code null} bez odpowiedzi
     * @param answersLast30Days         odpowiedzi z ostatnich 30 dni
     * @param accuracyLast30Days        procent poprawnych z ostatnich 30 dni; {@code null} bez odpowiedzi
     * @param confidentMistakesLast30Days błędne odpowiedzi bez „Nie jestem pewien" z ostatnich 30 dni
     */
    public record Metrics(
            int totalQuestions,
            int solvedQuestions,
            int matureQuestions,
            int dueQuestions,
            long answers,
            long correctAnswers,
            Double accuracyRate,
            long answersLast30Days,
            Double accuracyLast30Days,
            long confidentMistakesLast30Days
    ) {
    }
}
