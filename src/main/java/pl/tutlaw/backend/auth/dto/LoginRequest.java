package pl.tutlaw.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String privateEmail,
        @NotBlank String password
) {}