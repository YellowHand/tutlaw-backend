package pl.gradusiuris.backend.review.dto;

import pl.gradusiuris.backend.question.CorrectOption;
import pl.gradusiuris.backend.question.dto.LawChangeInfo;
import pl.gradusiuris.backend.flashcard.FlashcardStatus;

import java.time.LocalDate;

/**
 * Wynik odpowiedzi w teście.
 *
 * @param lapses ile razy użytkownik pomylił się w tym pytaniu (łącznie z tą odpowiedzią)
 * @param leech  czy pytanie jest już „trudne" (liczba błędów osiągnęła próg), czyli warto zaproponować zawieszenie
 * @param flashcardUnlocked prawda tylko wtedy, gdy ta odpowiedź właśnie dodała fiszkę pytania do Fiszek
 *                          (pierwszy pewny błąd w tym pytaniu)
 * @param flashcard         stan fiszki tego pytania dla użytkownika po tej odpowiedzi: czy można ją dodać
 *                          („Dodaj do fiszek"), czy już jest w Fiszkach (dodana ręcznie albo przez błąd) albo nie ma jej
 * @param lawChange         zmiana w prawie dotykająca pytania ({@code null}, gdy jej nie ma)
 */
public record TestAnswerResultResponse(
        boolean correct,
        CorrectOption correctOption,
        String explanation,
        String legalBasis,
        LocalDate validAsOf,
        int lapses,
        boolean leech,
        boolean flashcardUnlocked,
        FlashcardStatus flashcard,
        boolean newQuestion,
        LawChangeInfo lawChange
) {
}