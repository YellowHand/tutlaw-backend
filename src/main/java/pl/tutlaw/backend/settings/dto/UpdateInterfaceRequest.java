package pl.tutlaw.backend.settings.dto;

/** Zmiana ustawień interfejsu. Pole puste ({@code null}) zostaje bez zmian. */
public record UpdateInterfaceRequest(
        Boolean testHintHidden,
        Boolean flashcardHintHidden,
        Boolean questionTimerHidden
) {
}
