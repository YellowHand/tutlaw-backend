package pl.gradusiuris.backend.user.dto;

/** Kim jest zalogowany: adres e-mail i numer konta (ten sam numer, który widać w dziennikach serwera i w Sentry). */
public record MeResponse(String email, Long id) {}
