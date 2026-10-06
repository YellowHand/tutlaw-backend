package pl.tutlaw.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Email @NotBlank String privateEmail,
        @Email @NotBlank String universityEmail,
        @NotBlank @Size(min = 8) String password
) {}