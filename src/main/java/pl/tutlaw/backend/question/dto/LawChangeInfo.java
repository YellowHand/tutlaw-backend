package pl.tutlaw.backend.question.dto;

import pl.tutlaw.backend.question.Question;

import java.time.LocalDate;

/**
 * Zmiana w prawie dotykająca pytania, pokazywana przy nim jako plakietka „Zmiana w prawie: data".
 *
 * @param changedAt dzień wejścia zmiany w życie (albo późniejszej aktualizacji pytania)
 * @param note      krótki opis zmiany; {@code null}, gdy go nie wpisano
 */
public record LawChangeInfo(LocalDate changedAt, String note) {

    /** Zmiana pytania albo {@code null}, gdy pytanie nie ma daty zmiany. */
    public static LawChangeInfo of(Question question) {
        if (question.getLawChangedAt() == null) {
            return null;
        }
        return new LawChangeInfo(question.getLawChangedAt(), question.getLawChangeNote());
    }
}
