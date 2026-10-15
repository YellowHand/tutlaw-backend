-- Etap 31: własne testy zapisywane jako sesje. Sesja powstaje w chwili ułożenia testu (zapisujemy konfigurację i
-- wylosowane pytania), a odpowiedzi dopisują się do niej w trakcie. Test, którego nikt nie skończył, zostaje na liście
-- jako "nieukończony" (finished_at jest wtedy puste). Powtórki, "Popraw błędy" i egzamin próbny tu nie trafiają.
-- Sesje znikają razem z kontem (ON DELETE CASCADE).
CREATE TABLE custom_test_sessions (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    started_at      TIMESTAMP     NOT NULL,
    finished_at     TIMESTAMP,
    kind            VARCHAR(10)   NOT NULL,
    -- Nazwa zakresu w chwili układania testu (np. "Kodeks karny i Kodeks cywilny"), żeby lista była czytelna po zmianie nazw.
    scope_label     VARCHAR(300)  NOT NULL,
    -- Wybrane rodzaje i akty prawne (numery oddzielone przecinkami); oba puste = cały materiał. Z nich da się ułożyć test ponownie.
    category_ids    VARCHAR(2000) NOT NULL DEFAULT '',
    subject_ids     VARCHAR(2000) NOT NULL DEFAULT '',
    -- Filtr pochodzenia pytań obowiązujący w chwili układania testu (kody oddzielone przecinkami); NULL = wszystkie.
    origins         VARCHAR(200),
    requested_count INTEGER       NOT NULL,
    available_count INTEGER       NOT NULL,
    CONSTRAINT chk_custom_test_sessions_kind CHECK (kind IN ('ALL', 'NEW', 'MISTAKES', 'DUE')),
    CONSTRAINT chk_custom_test_sessions_counts CHECK (requested_count BETWEEN 1 AND 150 AND available_count >= 1)
);

-- Lista "Moje testy": od najnowszego.
CREATE INDEX idx_custom_test_sessions_user_started ON custom_test_sessions (user_id, started_at DESC);

-- Pytania sesji w kolejności, w jakiej pokazano je użytkownikowi, z jego odpowiedziami. Wiersz bez odpowiedzi
-- (answered_at IS NULL) to pytanie, do którego jeszcze nie doszedł.
CREATE TABLE custom_test_session_questions (
    id                 BIGSERIAL PRIMARY KEY,
    session_id         BIGINT           NOT NULL REFERENCES custom_test_sessions (id) ON DELETE CASCADE,
    card_id            BIGINT           NOT NULL REFERENCES cards (id) ON DELETE CASCADE,
    position           INTEGER          NOT NULL,
    -- Odpowiedź pod oryginalnym kluczem z bazy (nie literą z ekranu, bo odpowiedzi w teście są mieszane).
    selected_option    VARCHAR(1),
    was_correct        BOOLEAN,
    was_guessed        BOOLEAN          NOT NULL DEFAULT FALSE,
    time_spent_seconds DOUBLE PRECISION,
    answered_at        TIMESTAMP,
    CONSTRAINT uq_custom_test_session_questions_position UNIQUE (session_id, position),
    CONSTRAINT uq_custom_test_session_questions_card UNIQUE (session_id, card_id),
    CONSTRAINT chk_custom_test_session_questions_option CHECK (selected_option IS NULL OR selected_option IN ('A', 'B', 'C')),
    CONSTRAINT chk_custom_test_session_questions_answer CHECK (
        (answered_at IS NULL) = (selected_option IS NULL) AND (answered_at IS NULL) = (was_correct IS NULL))
);

CREATE INDEX idx_custom_test_session_questions_card ON custom_test_session_questions (card_id);
