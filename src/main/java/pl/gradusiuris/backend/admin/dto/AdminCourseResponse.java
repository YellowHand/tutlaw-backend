package pl.gradusiuris.backend.admin.dto;

import java.util.List;

/**
 * Przedmiot Studenta w panelu administratora.
 *
 * @param actIds    numery aktów i zagadnień przedmiotu (także ukrytych)
 * @param userCount ilu studentów wybrało ten przedmiot
 */
public record AdminCourseResponse(Long id, String slug, String name, int sortOrder, List<Long> actIds, long userCount) {
}
