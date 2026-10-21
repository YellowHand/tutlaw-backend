package pl.gradusiuris.backend.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "podaj adres e-mail") @Email(message = "nieprawidłowy adres e-mail")
        @Size(max = 254, message = "adres jest za długi") String privateEmail,
        @NotBlank(message = "podaj adres e-mail") @Email(message = "nieprawidłowy adres e-mail")
        @Size(max = 254, message = "adres jest za długi") String universityEmail,
        @NotBlank(message = "podaj hasło") @Size(min = 8, max = 72, message = "hasło musi mieć od 8 do 72 znaków")
        String password,
        @NotNull(message = "wymagana jest akceptacja regulaminu i polityki prywatności")
        @AssertTrue(message = "wymagana jest akceptacja regulaminu i polityki prywatności") Boolean acceptedTerms
) {}
