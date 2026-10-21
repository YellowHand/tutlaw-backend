package pl.gradusiuris.backend.auth.dto;

/**
 * Para tokenów po zalogowaniu, zmianie hasła i odświeżeniu. {@code accessToken} to krótki JWT wysyłany z każdym
 * żądaniem, {@code refreshToken} służy tylko do wystawienia następnej pary (POST /api/auth/refresh).
 */
public record AuthResponse(String accessToken, String refreshToken) {}
