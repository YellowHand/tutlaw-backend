package pl.tutlaw.backend.settings.dto;

/**
 * Ustawienia interfejsu zapisane na koncie.
 *
 * @param testHintHidden       podpowiedź o skrótach klawiszowych w testach jest stale ukryta (przycisk „×")
 * @param flashcardHintHidden  to samo w fiszkach
 * @param questionTimerHidden  licznik czasu przy pytaniu jest ukryty
 * @param testAnswers          ile odpowiedzi w testach użytkownik już dał, liczone do {@code ANSWERS_CAP} (10): na tej
 *                             podstawie podpowiedź znika po 10 odpowiedziach, także na nowym urządzeniu
 * @param flashcardReviews     ile ocen fiszek użytkownik już dał, liczone do 10
 */
public record InterfaceSettingsResponse(
        boolean testHintHidden,
        boolean flashcardHintHidden,
        boolean questionTimerHidden,
        int testAnswers,
        int flashcardReviews
) {
}
