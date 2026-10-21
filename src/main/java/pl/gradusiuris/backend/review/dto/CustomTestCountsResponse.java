package pl.gradusiuris.backend.review.dto;

/**
 * Ile pytań ma każdy rodzaj w wybranym zakresie własnego testu (te same warunki co przy losowaniu). Rodzaje nakładają się:
 * jedno pytanie może być jednocześnie zaległe i błędne, więc ich suma nie musi dawać {@code all}.
 *
 * @param all           wszystkie dostępne pytania
 * @param newQuestions  jeszcze nierozwiązywane
 * @param mistakes      z błędną ostatnią odpowiedzią (z ostatnich dni, jak „Moje błędy")
 * @param due           z minionym terminem powtórki
 * @param lawChanges   ze zmianą w prawie z ostatniego roku
 * @param timeLimits   pytania o terminy (zatwierdzone oznaczenie administratora)
 * @param traps        pułapki (zatwierdzone oznaczenie administratora któregokolwiek podtypu)
 */
public record CustomTestCountsResponse(
        int all, int newQuestions, int mistakes, int due, int lawChanges, int timeLimits, int traps) {
}
