-- Etap 2: indeksy pod zapytania z kolejki i statystyk oraz ograniczenia pilnujące poprawności danych.
-- Dotychczas poza kluczami głównymi i UNIQUE nie było żadnych indeksów, więc każde zapytanie
-- o karty źródła, rozdziału czy terminy powtórek czytało całe tabele.

-- ---------------------------------------------------------------------------
-- Indeksy
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_cards_source_id ON cards (source_id);
CREATE INDEX IF NOT EXISTS idx_cards_chapter_id ON cards (chapter_id);
CREATE INDEX IF NOT EXISTS idx_chapters_source_order ON chapters (source_id, order_index);
CREATE INDEX IF NOT EXISTS idx_sources_subject_id ON sources (subject_id);
CREATE INDEX IF NOT EXISTS idx_subjects_category_id ON subjects (category_id);
CREATE INDEX IF NOT EXISTS idx_card_tags_tag_id ON card_tags (tag_id);
CREATE INDEX IF NOT EXISTS idx_user_chapter_progress_chapter_id ON user_chapter_progress (chapter_id);
CREATE INDEX IF NOT EXISTS idx_email_verification_tokens_user_id ON email_verification_tokens (user_id);

-- Zaległe powtórki użytkownika (licznik na pasku bocznym, kolejka, prognoza).
CREATE INDEX IF NOT EXISTS idx_user_card_state_user_due ON user_card_state (user_id, due_date);
CREATE INDEX IF NOT EXISTS idx_user_card_state_card_id ON user_card_state (card_id);

-- Aktywność, retencja i historia.
CREATE INDEX IF NOT EXISTS idx_review_logs_user_reviewed_at ON review_logs (user_id, reviewed_at);
CREATE INDEX IF NOT EXISTS idx_review_logs_card_id ON review_logs (card_id);
-- Dzienny limit nowych kart liczy tylko pierwsze powtórki (bez elapsed_days).
CREATE INDEX IF NOT EXISTS idx_review_logs_first_reviews
    ON review_logs (user_id, mode, reviewed_at) WHERE elapsed_days IS NULL;

-- ---------------------------------------------------------------------------
-- Stan karty: Hibernate zapisuje wartości wielkimi literami (NEW, REVIEW...),
-- a domyślna wartość z V1 to małe 'new', której aplikacja nie potrafi odczytać.
-- ---------------------------------------------------------------------------
UPDATE user_card_state SET state = upper(state) WHERE state <> upper(state);
ALTER TABLE user_card_state ALTER COLUMN state SET DEFAULT 'NEW';

-- ---------------------------------------------------------------------------
-- Ograniczenia CHECK.
-- NOT VALID: baza pilnuje od teraz każdego nowego i zmienianego wiersza, ale nie sprawdza
-- wstecz tego, co już w niej jest (migracja nie wywróci się na starych, niedoskonałych danych).
-- Gdy będziesz pewien, że dane są czyste, możesz sprawdzić też stare wiersze:
--   ALTER TABLE cards VALIDATE CONSTRAINT chk_cards_type;   (i tak dalej dla każdego)
-- ---------------------------------------------------------------------------
ALTER TABLE cards
    ADD CONSTRAINT chk_cards_type
        CHECK (type IN ('DEFINITION', 'PAREMIA', 'COMPARISON', 'CLOZE')) NOT VALID;
ALTER TABLE cards
    ADD CONSTRAINT chk_cards_text_not_blank
        CHECK (length(btrim(front)) > 0 AND length(btrim(back)) > 0) NOT VALID;
ALTER TABLE cards
    ADD CONSTRAINT chk_cards_correct_option
        CHECK (correct_option IS NULL OR correct_option IN ('A', 'B', 'C')) NOT VALID;
-- Pytanie testowe (z poprawną odpowiedzią) musi mieć wszystkie trzy odpowiedzi.
ALTER TABLE cards
    ADD CONSTRAINT chk_cards_test_has_options
        CHECK (correct_option IS NULL
            OR (option_a IS NOT NULL AND option_b IS NOT NULL AND option_c IS NOT NULL)) NOT VALID;
ALTER TABLE cards
    ADD CONSTRAINT chk_cards_page_number
        CHECK (page_number IS NULL OR page_number >= 0) NOT VALID;

ALTER TABLE sources
    ADD CONSTRAINT chk_sources_type
        CHECK (source_type IN ('TEXTBOOK', 'FLAT_POOL')) NOT VALID;

ALTER TABLE users
    ADD CONSTRAINT chk_users_role
        CHECK (role IN ('ADMIN', 'STUDENT')) NOT VALID;

ALTER TABLE review_logs
    ADD CONSTRAINT chk_review_logs_rating
        CHECK (rating BETWEEN 1 AND 4) NOT VALID;
ALTER TABLE review_logs
    ADD CONSTRAINT chk_review_logs_mode
        CHECK (mode IN ('FLASHCARD', 'TEST')) NOT VALID;

ALTER TABLE user_card_state
    ADD CONSTRAINT chk_user_card_state_state
        CHECK (state IN ('NEW', 'LEARNING', 'REVIEW', 'RELEARNING')) NOT VALID;
ALTER TABLE user_card_state
    ADD CONSTRAINT chk_user_card_state_counters
        CHECK (reps >= 0 AND lapses >= 0 AND stability >= 0 AND difficulty >= 0) NOT VALID;
