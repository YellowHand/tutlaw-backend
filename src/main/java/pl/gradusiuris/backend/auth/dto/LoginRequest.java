package pl.gradusiuris.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "podaj adres e-mail") @Size(max = 254, message = "adres jest za długi") String privateEmail,
        @NotBlank(message = "podaj hasło") @Size(max = 256, message = "hasło jest za długie") String password
) {}
