package pl.gradusiuris.backend.admin.dto;

/**
 * Liczby do ekranu startowego panelu administratora. Pytania liczone razem z ukrytymi, bo to widok administratora.
 *
 * @param applications ile jest aplikacji (ścieżek z egzaminem), bez ścieżki Student
 * @param courses      ile jest przedmiotów Studenta
 * @param acts         ile jest aktów prawnych i zagadnień, razem z ukrytymi
 * @param hiddenActs   ile z nich jest ukrytych
 * @param sheets       ile jest arkuszy egzaminacyjnych, razem z ukrytymi
 * @param hiddenSheets ile z nich jest ukrytych
 * @param questions    ile jest wszystkich pytań
 * @param hiddenQuestions ile pytań jest ukrytych
 * @param newReports   ile zgłoszeń błędów w pytaniach czeka na rozpatrzenie
 */
public record AdminOverviewResponse(
        int applications,
        int courses,
        int fields,
        int acts,
        int hiddenActs,
        int sections,
        int sheets,
        int hiddenSheets,
        long questions,
        long hiddenQuestions,
        long newReports
) {
}
