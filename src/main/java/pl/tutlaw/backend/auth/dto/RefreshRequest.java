package pl.tutlaw.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshRequest(
        @NotBlank(message = "brak tokenu") @Size(max = 200, message = "nieprawidłowy token") String refreshToken
) {}
