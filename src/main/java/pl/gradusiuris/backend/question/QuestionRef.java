package pl.gradusiuris.backend.question;

import java.time.LocalDate;

/**
 * Odchudzony widok pytania do układania kolejek, liczników, planu do egzaminu i losowania: same numery i flagi,
 * bez treści. Przy tysiącach pytań i kilkudziesięciu w sesji ładowanie całych pytań tylko po to, żeby większość
 * odrzucić, byłoby marnotrawstwem.
 */
public interface QuestionRef {

    Long getId();

    Long getLegalActId();

    Long getLegalFieldId();

    Long getSourceId();

    Boolean getFlashcardHidden();

    /** Dzień wejścia w życie zmiany w prawie dotykającej pytania ({@code null}, gdy pytanie nie ma zmiany). */
    LocalDate getLawChangedAt();
}
