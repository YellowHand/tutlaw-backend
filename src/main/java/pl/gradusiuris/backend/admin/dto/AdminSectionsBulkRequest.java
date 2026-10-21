package pl.gradusiuris.backend.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Wiele rozdziałów naraz (jeden na pozycję listy), w podanej kolejności na koniec dotychczasowej listy. */
public record AdminSectionsBulkRequest(@NotNull @Size(max = 300) List<String> names) {
}
