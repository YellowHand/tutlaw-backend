package pl.tutlaw.backend.admin.dto;

import pl.tutlaw.backend.legal.LegalActKind;

/** Lekka pozycja listy wyboru aktów (przypisywanie do aplikacji i przedmiotów): wszystkie akty, razem z ukrytymi. */
public record AdminActOptionResponse(
        Long id, String name, String shortName, Long fieldId, String fieldName, LegalActKind kind, boolean hidden) {
}
