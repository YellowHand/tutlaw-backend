package pl.gradusiuris.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Samo konto (adres, którym się loguje): do "zapomniałem hasła" i "wyślij link aktywacyjny ponownie". */
public record EmailRequest(
        @NotBlank(message = "podaj adres e-mail") @Size(max = 254, message = "adres jest za długi") String privateEmail
) {}
