package pl.tutlaw.backend.mark;

/** Kto nadał oznaczenie. */
public enum MarkOrigin {
    /** Zaproponował automat (reguły w {@link QuestionMarkDetector}). */
    AUTO,
    /** Nadał administrator. */
    MANUAL
}
