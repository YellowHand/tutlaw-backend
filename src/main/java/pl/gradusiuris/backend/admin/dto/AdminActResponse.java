package pl.gradusiuris.backend.admin.dto;

import pl.gradusiuris.backend.legal.LegalActKind;

import java.util.List;

/**
 * Akt prawny albo zagadnienie w panelu administratora.
 *
 * @param questionCount       wszystkie pytania aktu, razem z ukrytymi
 * @param hiddenQuestionCount ile z nich jest ukrytych
 * @param trackIds            aplikacje, do których akt jest przypisany
 * @param courseIds           przedmioty Studenta, do których akt jest przypisany
 */
public record AdminActResponse(
        Long id,
        String slug,
        String name,
        String shortName,
        Long fieldId,
        String fieldName,
        LegalActKind kind,
        int sortOrder,
        boolean hidden,
        long questionCount,
        long hiddenQuestionCount,
        int sectionCount,
        List<Long> trackIds,
        List<Long> courseIds
) {
}
