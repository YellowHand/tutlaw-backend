package pl.tutlaw.backend.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Usunięcie konta wymaga podania hasła, żeby nie dało się go usunąć z przypadkowo zostawionej sesji. */
public record DeleteAccountRequest(
        @NotBlank(message = "podaj hasło") @Size(max = 256, message = "hasło jest za długie") String password
) {}
