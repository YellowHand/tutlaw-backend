package pl.gradusiuris.backend.question;

/** Treść pytania i jego odpowiedzi, bez reszty pól: do analizy tekstu (oznaczenia pytań). */
public interface QuestionText {

    Long getId();

    String getBody();

    String getOptionA();

    String getOptionB();

    String getOptionC();
}
