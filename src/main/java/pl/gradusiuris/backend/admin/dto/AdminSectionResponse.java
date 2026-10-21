package pl.gradusiuris.backend.admin.dto;

/**
 * Rozdział aktu w panelu administratora.
 *
 * @param questionCount       wszystkie pytania rozdziału, razem z ukrytymi
 * @param hiddenQuestionCount ile z nich jest ukrytych
 */
public record AdminSectionResponse(Long id, Long actId, String name, int sortOrder, long questionCount, long hiddenQuestionCount) {
}
