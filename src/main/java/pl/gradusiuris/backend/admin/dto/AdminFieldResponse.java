package pl.gradusiuris.backend.admin.dto;

/** Dziedzina prawa (tylko do odczytu): akt prawny należy do jednej dziedziny. */
public record AdminFieldResponse(Long id, String name, int sortOrder, int actCount) {
}
