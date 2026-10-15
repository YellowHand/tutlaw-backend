-- Etap 31: token odświeżający. Token dostępu (JWT) żyje teraz krótko (domyślnie 30 minut), a użytkownik pozostaje
-- zalogowany dzięki tokenowi odświeżającemu ważnemu 30 dni. Każde użycie wymienia go na nowy (rotacja).
--
-- W bazie trzymamy tylko skrót SHA-256 tokenu (64 znaki szesnastkowe), tak jak przy linkach resetu hasła: wyciek bazy
-- nie daje nikomu działających sesji. Tokeny jednej sesji (jednego logowania na jednym urządzeniu) tworzą "rodzinę"
-- (family_id). Gdy ktoś użyje drugi raz tokenu, który już wymieniono, uznajemy to za kradzież i unieważniamy
-- całą rodzinę. Zmiana i reset hasła unieważniają wszystkie tokeny konta, a usunięcie konta kasuje je kaskadowo.
CREATE TABLE refresh_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    family_id  VARCHAR(36) NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT now(),
    expires_at TIMESTAMP   NOT NULL,
    rotated_at TIMESTAMP,
    revoked_at TIMESTAMP
);

CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
