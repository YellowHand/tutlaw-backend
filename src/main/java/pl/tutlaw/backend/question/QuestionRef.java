package pl.tutlaw.backend.question;

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
}
