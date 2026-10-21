package pl.tutlaw.backend.exam.dto;

import java.util.List;

/**
 * Egzaminy MS do wyboru na ekranie egzaminu: arkusze egzaminów wstępnych Ministerstwa Sprawiedliwości z poprzednich
 * lat, pogrupowane według aplikacji. Tylko arkusze, z których da się teraz ułożyć egzamin (są w nich widoczne pytania,
 * których użytkownik nie zawiesił w testach); aplikacja bez takiego arkusza nie występuje na liście.
 *
 * @param applications aplikacje z arkuszami: najpierw aplikacja użytkownika, potem pozostałe w kolejności katalogu
 */
public record ExamSheetsResponse(List<Application> applications) {

    /**
     * @param shortName skrót aplikacji (np. „Notarialna"), taki jak w nazwach arkuszy
     * @param own       czy to aplikacja wybrana przez użytkownika
     * @param sheets    arkusze od najnowszego roku
     */
    public record Application(Long trackId, String slug, String name, String shortName, boolean own, List<Sheet> sheets) {
    }

    /**
     * @param id            numer arkusza (źródła pytań), z którym startuje egzamin
     * @param year          rok egzaminu
     * @param questionCount ile pytań będzie na egzaminie: widoczne pytania arkusza bez zawieszonych w testach, najwyżej 150
     */
    public record Sheet(Long id, int year, int questionCount) {
    }
}
