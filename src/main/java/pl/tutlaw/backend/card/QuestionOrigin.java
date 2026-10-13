package pl.tutlaw.backend.card;

/**
 * Pochodzenie karty. Kod w angielskim, polskie etykiety ("Pytanie autorskie", "Aplikacja notarialna" itd.)
 * pokazuje interfejs. Dla wszystkich poza {@link #AUTHORED} trzeba podać rok egzaminu.
 */
public enum QuestionOrigin {
    /** Pytanie napisane przez nas. */
    AUTHORED,
    /** Aplikacja adwokacka i radcowska. */
    ADVOCATE_LEGAL_ADVISER,
    /** Aplikacja sędziowska i prokuratorska. */
    JUDGE_PROSECUTOR,
    /** Aplikacja notarialna. */
    NOTARY,
    /** Aplikacja uzupełniająca sędziowska. */
    SUPPLEMENTARY_JUDGE,
    /** Aplikacja komornicza. */
    BAILIFF;

    /** Czy pytanie z tego źródła musi mieć rok egzaminu. */
    public boolean requiresYear() {
        return this != AUTHORED;
    }
}
