-- Etap 5C: treść pytań.
-- 1) Podstawa prawna i stan prawny na dzień (obie kolumny opcjonalne; pokazujemy je uczącemu się przy odpowiedzi).
-- 2) Zgłoszenia błędów w pytaniach od użytkowników.

ALTER TABLE cards ADD COLUMN legal_basis TEXT;
ALTER TABLE cards ADD COLUMN valid_as_of DATE;
ALTER TABLE cards ADD CONSTRAINT chk_cards_legal_basis_length
    CHECK (legal_basis IS NULL OR char_length(legal_basis) <= 500);

CREATE TABLE card_reports
(
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    card_id     BIGINT      NOT NULL REFERENCES cards (id) ON DELETE CASCADE,
    reason      VARCHAR(20) NOT NULL,
    comment     TEXT,
    status      VARCHAR(20) NOT NULL DEFAULT 'NEW',
    created_at  TIMESTAMP   NOT NULL DEFAULT now(),
    resolved_at TIMESTAMP,
    CONSTRAINT chk_card_reports_reason CHECK (reason IN ('WRONG_ANSWER', 'OUTDATED', 'UNCLEAR', 'OTHER')),
    CONSTRAINT chk_card_reports_status CHECK (status IN ('NEW', 'RESOLVED', 'REJECTED')),
    CONSTRAINT chk_card_reports_comment_length CHECK (comment IS NULL OR char_length(comment) <= 1000)
);

-- Jeden otwarty (nierozpatrzony) wpis na użytkownika i pytanie: kolejne zgłoszenie tego samego uzupełnia poprzednie.
CREATE UNIQUE INDEX uq_card_reports_open_per_user_card ON card_reports (user_id, card_id) WHERE status = 'NEW';
CREATE INDEX idx_card_reports_status_created ON card_reports (status, created_at);
CREATE INDEX idx_card_reports_card_id ON card_reports (card_id);
CREATE INDEX idx_card_reports_user_created ON card_reports (user_id, created_at DESC);
