-- Etap 7: ustawienia nauki, blokada optymistyczna.

-- 1) Blokada optymistyczna stanu karty: dwie równoległe odpowiedzi na tę samą kartę (dwa okna przeglądarki)
--    nie nadpiszą się po cichu, drugi zapis dostaje konflikt (HTTP 409).
ALTER TABLE user_card_state ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- 2) Ustawienia nauki użytkownika. NULL w kolumnie liczbowej oznacza "wartość domyślna z konfiguracji serwera".
CREATE TABLE user_settings (
    user_id                    BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    new_cards_per_day          INTEGER,
    new_test_questions_per_day INTEGER,
    max_reviews_per_day        INTEGER,
    desired_retention          DOUBLE PRECISION,
    exam_date                  DATE,
    shuffle_options            BOOLEAN   NOT NULL DEFAULT TRUE,
    updated_at                 TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_user_settings_new_cards CHECK (new_cards_per_day IS NULL OR new_cards_per_day BETWEEN 0 AND 200),
    CONSTRAINT chk_user_settings_new_tests CHECK (new_test_questions_per_day IS NULL OR new_test_questions_per_day BETWEEN 0 AND 500),
    CONSTRAINT chk_user_settings_reviews CHECK (max_reviews_per_day IS NULL OR max_reviews_per_day BETWEEN 10 AND 1000),
    CONSTRAINT chk_user_settings_retention CHECK (desired_retention IS NULL OR desired_retention BETWEEN 0.70 AND 0.97)
);
