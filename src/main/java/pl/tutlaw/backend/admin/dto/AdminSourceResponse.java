package pl.tutlaw.backend.admin.dto;

import pl.tutlaw.backend.source.SourceKind;

/** Źródło pytań do list wyboru w panelu: arkusz egzaminu albo pytania autorskie (także ukryte, z adnotacją). */
public record AdminSourceResponse(Long id, String slug, String name, SourceKind kind, boolean hidden) {
}
