package pl.tutlaw.backend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Import pytań z pliku wczytanego do tekstu. {@code dryRun} (domyślnie prawda) tylko sprawdza plik i zwraca raport, nic
 * nie zapisując; {@code false} zapisuje wszystkie poprawne pytania, ale tylko wtedy, gdy w pliku nie ma żadnego błędu.
 * {@code defaultAct} i {@code defaultSource} (adres albo nazwa) uzupełniają wiersze, w których tych pól brakuje.
 */
public record ImportQuestionsRequest(
        @NotNull(message = "wybierz format pliku") ImportFormat format,
        @NotBlank(message = "plik jest pusty") @Size(max = 4_000_000, message = "plik jest za duży (najwyżej 4 MB)") String content,
        Boolean dryRun,
        String defaultAct,
        String defaultSource
) {
}
