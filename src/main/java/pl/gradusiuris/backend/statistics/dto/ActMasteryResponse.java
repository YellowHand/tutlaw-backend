package pl.gradusiuris.backend.statistics.dto;

/** Opanowanie jednego aktu prawnego z puli użytkownika. */
public record ActMasteryResponse(
        Long actId,
        String name,
        String shortName,
        Long fieldId,
        String fieldName,
        int totalQuestions,
        int questionsStarted,
        int matureQuestions,
        double masteryPercentage
) {
}
