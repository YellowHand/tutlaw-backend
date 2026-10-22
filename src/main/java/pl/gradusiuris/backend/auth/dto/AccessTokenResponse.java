package pl.gradusiuris.backend.auth.dto;

/**
 * Odpowiedź po zalogowaniu, zmianie hasła i odświeżeniu sesji. Zawiera tylko krótki token dostępu (JWT, 30 minut), który
 * frontend trzyma w pamięci i dołącza do żądań. Token odświeżający NIE jest tu zwracany: leży w ciasteczku HttpOnly
 * (zob. {@code RefreshCookie}), którego skrypty na stronie nie mogą odczytać.
 */
public record AccessTokenResponse(String accessToken) {}
