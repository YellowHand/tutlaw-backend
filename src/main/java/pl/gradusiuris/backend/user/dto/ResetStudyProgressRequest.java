package pl.gradusiuris.backend.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Zerowanie postępów wymaga wpisania słowa {@code WYZERUJ}, żeby nie dało się go wykonać przypadkiem. */
public record ResetStudyProgressRequest(
        @NotBlank(message = "wpisz słowo WYZERUJ") @Size(max = 50, message = "potwierdzenie jest za długie") String confirm
) {
}
