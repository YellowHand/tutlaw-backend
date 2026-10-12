-- Etap 8: trudne pytania. Użytkownik może zawiesić pytanie, z którym sobie nie radzi albo które chce odłożyć:
-- zawieszone pytanie znika z kolejek i liczników powtórek, ale zachowuje cały dotychczasowy postęp.
ALTER TABLE user_card_state
    ADD COLUMN suspended    BOOLEAN   NOT NULL DEFAULT FALSE,
    ADD COLUMN suspended_at TIMESTAMP;

-- Zawieszona karta zawsze ma datę zawieszenia, a niezawieszona jej nie ma.
ALTER TABLE user_card_state
    ADD CONSTRAINT chk_user_card_state_suspended_at
        CHECK ((suspended AND suspended_at IS NOT NULL) OR (NOT suspended AND suspended_at IS NULL));

-- Lista zawieszonych pytań użytkownika (zwykle niewielki ułamek wierszy, stąd indeks częściowy).
CREATE INDEX idx_user_card_state_suspended ON user_card_state (user_id) WHERE suspended;
