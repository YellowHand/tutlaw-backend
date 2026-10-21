package pl.tutlaw.backend.exam.dto;

/**
 * Ile pytań można teraz wylosować na egzamin z każdego źródła (bez zawężenia puli do aktów, bez pytań zawieszonych
 * w testach). Ekran egzaminu pokazuje te liczby przy wyborze źródła.
 *
 * @param all       ze wszystkich źródeł
 * @param ministry  tylko z arkuszy egzaminacyjnych Ministerstwa Sprawiedliwości
 * @param authored  tylko pytania autorskie
 */
public record ExamAvailabilityResponse(int all, int ministry, int authored) {
}
