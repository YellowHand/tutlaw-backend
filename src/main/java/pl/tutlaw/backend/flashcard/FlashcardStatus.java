package pl.tutlaw.backend.flashcard;

/**
 * Stan fiszki pytania testowego z punktu widzenia jednego użytkownika (pokazywany przy odpowiedzi w teście).
 */
public enum FlashcardStatus {
    /** Pytanie nie ma widocznej fiszki (np. została ukryta), więc nie ma czego dodawać. */
    UNAVAILABLE,
    /** Fiszki jeszcze nie ma w Fiszkach użytkownika: można ją dodać przyciskiem „Dodaj do fiszek". */
    AVAILABLE,
    /** Użytkownik sam dodał fiszkę: jest w Fiszkach i może ją stamtąd usunąć („Usuń z fiszek"). */
    ADDED_MANUALLY,
    /** Fiszka trafiła do Fiszek po pewnym błędzie w tym pytaniu (bez „Nie jestem pewien"); nie da się jej wycofać. */
    ADDED_BY_MISTAKE
}
