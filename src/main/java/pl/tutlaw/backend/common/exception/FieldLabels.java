package pl.tutlaw.backend.common.exception;

import java.util.Map;

/** Polskie nazwy pól formularzy w komunikatach walidacji ("Hasło: musi mieć od 8 do 72 znaków" zamiast "password: ..."). */
public final class FieldLabels {

    private static final Map<String, String> LABELS = Map.of(
            "privateEmail", "Mail prywatny",
            "universityEmail", "Mail uczelniany",
            "password", "Hasło",
            "newPassword", "Nowe hasło",
            "acceptedTerms", "Regulamin",
            "token", "Link"
    );

    private FieldLabels() {
    }

    /** Etykieta pola dla użytkownika. Pola, których nie znamy, zostają pod nazwą z kodu. */
    public static String label(String field) {
        return LABELS.getOrDefault(field, field);
    }
}
