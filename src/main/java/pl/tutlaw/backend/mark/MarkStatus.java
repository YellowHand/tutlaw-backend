package pl.tutlaw.backend.mark;

/** Stan oznaczenia. W testach liczą się tylko zatwierdzone. */
public enum MarkStatus {
    /** Propozycja automatu czeka na przejrzenie. */
    SUGGESTED,
    /** Zatwierdzone: pytanie wchodzi do testów „Pytania z terminów" albo „Pułapki". */
    CONFIRMED,
    /** Odrzucone: automat nie zaproponuje go ponownie. */
    REJECTED
}
