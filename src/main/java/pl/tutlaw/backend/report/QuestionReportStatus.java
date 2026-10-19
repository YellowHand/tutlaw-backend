package pl.tutlaw.backend.report;

/** Stan zgłoszenia. Rozpatrywanie odbywa się poza aplikacją (zapytaniem SQL), więc zmienia się je ręcznie w bazie. */
public enum QuestionReportStatus {
    NEW,
    RESOLVED,
    REJECTED
}
