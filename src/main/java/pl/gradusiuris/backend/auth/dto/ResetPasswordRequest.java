package pl.gradusiuris.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "brak tokenu") @Size(max = 200, message = "nieprawidłowy token") String token,
        @NotBlank(message = "podaj nowe hasło") @Size(min = 8, max = 72, message = "hasło musi mieć od 8 do 72 znaków")
        String newPassword
) {}
