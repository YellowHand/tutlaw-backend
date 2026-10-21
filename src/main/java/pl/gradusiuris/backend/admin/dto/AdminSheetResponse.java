package pl.gradusiuris.backend.admin.dto;

/**
 * Arkusz egzaminacyjny (aplikacja i rok) w panelu administratora.
 *
 * @param trackName           krótka nazwa aplikacji
 * @param questionCount       wszystkie pytania arkusza, razem z ukrytymi
 * @param hiddenQuestionCount ile z nich jest ukrytych
 */
public record AdminSheetResponse(
        Long id,
        String slug,
        String name,
        Long trackId,
        String trackName,
        int examYear,
        boolean hidden,
        long questionCount,
        long hiddenQuestionCount
) {
}
