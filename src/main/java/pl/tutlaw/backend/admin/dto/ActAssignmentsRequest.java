package pl.tutlaw.backend.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Pełne przypisanie aktu: do jakich aplikacji i przedmiotów Studenta należy (zastępuje dotychczasowe). */
public record ActAssignmentsRequest(
        @NotNull @Size(max = 100) List<Long> trackIds,
        @NotNull @Size(max = 200) List<Long> courseIds
) {
}
