-- Etap 5A: reset hasła i zgoda na regulamin.

-- Tokeny resetu hasła. W bazie trzymamy tylko skrót SHA-256 tokenu (64 znaki szesnastkowe), a sam token
-- jest wyłącznie w mailu, więc wyciek bazy nie daje nikomu działających linków do zmiany hasła.
CREATE TABLE password_reset_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64)  NOT NULL UNIQUE,
    expires_at TIMESTAMP    NOT NULL,
    used_at    TIMESTAMP,
    created_at TIMESTAMP    NOT NULL DEFAULT now()
);

-- Ostatni token użytkownika (limit częstotliwości wysyłania maili) i unieważnianie pozostałych po zmianie hasła.
CREATE INDEX idx_password_reset_tokens_user_created
    ON password_reset_tokens (user_id, created_at DESC);

-- Kiedy użytkownik zaakceptował regulamin i politykę prywatności. NULL = konto sprzed wprowadzenia regulaminu.
ALTER TABLE users
    ADD COLUMN terms_accepted_at TIMESTAMP;
