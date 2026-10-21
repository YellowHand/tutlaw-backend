package pl.tutlaw.backend.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Pełna lista aktów aplikacji albo przedmiotu (zastępuje dotychczasową). Pusta lista zdejmuje wszystkie akty. */
public record ActIdsRequest(@NotNull @Size(max = 2000) List<Long> actIds) {
}
