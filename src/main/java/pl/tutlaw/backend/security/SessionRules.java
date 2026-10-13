package pl.tutlaw.backend.security;

/**
 * Czy sesja (token JWT) nadal obowiązuje po zmianie albo resecie hasła. Osobno od filtra, żeby dało się to sprawdzić
 * zwykłym testem.
 */
public final class SessionRules {

    private SessionRules() {
    }

    /**
     * @param issuedAtEpochSeconds kiedy wystawiono token (claim iat); null, gdy go brak
     * @param tokensValidFrom      od kiedy konto honoruje tokeny (sekundy od 1970); null = bez ograniczenia
     * @return true, gdy token wystawiono nie wcześniej niż w chwili ograniczenia. Token bez znacznika czasu nie
     *         przechodzi, jeśli konto ma ograniczenie (nie da się wykazać, że jest nowszy).
     */
    public static boolean isSessionValid(Long issuedAtEpochSeconds, Long tokensValidFrom) {
        if (tokensValidFrom == null) {
            return true;
        }
        return issuedAtEpochSeconds != null && issuedAtEpochSeconds >= tokensValidFrom;
    }
}
