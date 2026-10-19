package pl.tutlaw.backend.statistics.dto;

import pl.tutlaw.backend.source.SourceKind;

import java.util.List;

/**
 * Statystyki Testów według źródła pytań (zakładka „Statystyki"). Zawsze trzy grupy w stałej kolejności (własne
 * arkusze, arkusze innych ścieżek, pytania autorskie), także te bez pytań (same zera). Nie podlega filtrowi źródeł
 * z Testów ani zawężeniu do aktów: pokazuje całą pulę ścieżki.
 *
 * @param groups grupy źródeł
 */
public record SourceStatsResponse(List<GroupEntry> groups) {

    /**
     * @param group   OWN_SHEETS, OTHER_SHEETS albo AUTHORED
     * @param stats   suma ze wszystkich źródeł grupy
     * @param sources źródła grupy (arkusze od najnowszego roku); puste źródła są pomijane
     */
    public record GroupEntry(String group, Metrics stats, List<SourceEntry> sources) {
    }

    public record SourceEntry(Long id, String name, SourceKind kind, Integer examYear, Metrics stats) {
    }

    /**
     * Liczby dotyczą pytań dostępnych teraz dla użytkownika (nieukrytych, z aktów jego ścieżki).
     *
     * @param totalQuestions              ile jest takich pytań
     * @param solvedQuestions             ile z nich rozwiązano co najmniej raz
     * @param matureQuestions             ile jest utrwalonych (stabilność powyżej progu, jak „Opanowane" w statystykach)
     * @param dueQuestions                ile ma już miniony termin powtórki (bez zawieszonych)
     * @param answers                     wszystkie odpowiedzi w testach
     * @param correctAnswers              z tego poprawne (niezależnie od „Nie jestem pewien")
     * @param accuracyRate                procent poprawnych ze wszystkich odpowiedzi; {@code null} bez odpowiedzi
     * @param answersLast30Days           odpowiedzi z ostatnich 30 dni
     * @param accuracyLast30Days          procent poprawnych z ostatnich 30 dni; {@code null} bez odpowiedzi
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
