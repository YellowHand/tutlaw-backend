package pl.tutlaw.backend.settings.dto;

import pl.tutlaw.backend.card.QuestionOrigin;

/**
 * Wybór w oknie startowym i w Koncie: aplikacja, do której użytkownik się przygotowuje (jedna z pięciu, bez pytań
 * autorskich), albo {@code student = true} ("Jestem studentem / jeszcze nie wybrałem"): bez aplikacji, terminu
 * egzaminu i zawężenia pytań. Gdy {@code student} jest prawdą, {@code origin} jest pomijane.
 */
public record ChooseExamRequest(QuestionOrigin origin, Boolean student) {

    public boolean isStudent() {
        return Boolean.TRUE.equals(student);
    }
}
