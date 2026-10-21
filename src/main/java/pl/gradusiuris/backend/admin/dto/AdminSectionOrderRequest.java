package pl.gradusiuris.backend.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Nowa kolejność rozdziałów aktu: numery wszystkich jego rozdziałów w żądanej kolejności. */
public record AdminSectionOrderRequest(@NotNull @Size(max = 500) List<Long> ids) {
}
