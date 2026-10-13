package pl.tutlaw.backend.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Zmiana hasła zalogowanego użytkownika. Wymaga obecnego hasła, żeby nie dało się go zmienić z zostawionej sesji. */
public record ChangePasswordRequest(
        @NotBlank(message = "podaj obecne hasło") @Size(max = 256, message = "hasło jest za długie") String currentPassword,
        @NotBlank(message = "podaj nowe hasło") @Size(min = 8, max = 72, message = "hasło musi mieć od 8 do 72 znaków")
        String newPassword
) {}
