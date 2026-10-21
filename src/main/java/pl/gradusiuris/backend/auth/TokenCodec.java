package pl.gradusiuris.backend.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Generowanie i skracanie tokenów jednorazowych (link w mailu). */
final class TokenCodec {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenCodec() {
    }

    /** 256 bitów losowości zapisane jako 43 znaki bezpieczne w adresie URL. */
    static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Skrót SHA-256 zapisany szesnastkowo (64 znaki). W bazie trzymamy skrót, nie sam token. */
    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 nie jest dostępny w tej wersji Javy", e);
        }
    }
}
