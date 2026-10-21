package pl.gradusiuris.backend.report;

/** Powód zgłoszenia błędu w pytaniu. */
public enum QuestionReportReason {
    /** Zaznaczona jako poprawna odpowiedź jest błędna. */
    WRONG_ANSWER,
    /** Przepis się zmienił, pytanie jest nieaktualne. */
    OUTDATED,
    /** Literówka albo niejasne sformułowanie. */
    UNCLEAR,
    /** Inny problem (wymaga opisu). */
    OTHER
}
