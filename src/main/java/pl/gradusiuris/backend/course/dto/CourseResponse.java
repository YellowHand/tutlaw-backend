package pl.gradusiuris.backend.course.dto;

import java.util.List;

/**
 * Przedmiot studenta z aktami prawnymi i zagadnieniami, z których składa się jego materiał.
 *
 * @param questionCount pytania przedmiotu (ze wszystkich źródeł)
 */
public record CourseResponse(Long id, String name, String slug, int questionCount, List<Long> actIds) {
}
