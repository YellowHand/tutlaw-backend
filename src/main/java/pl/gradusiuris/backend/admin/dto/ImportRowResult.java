package pl.gradusiuris.backend.admin.dto;

/** Raport o jednym wierszu: numer, wynik, opis (dla powtórzeń i błędów) i początek treści pytania do rozpoznania wiersza. */
public record ImportRowResult(int row, ImportRowStatus status, String message, String preview) {
}
