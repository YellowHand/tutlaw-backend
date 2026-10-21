-- ======================================================================================================
-- Etap 34, poprawka 2: oznaczenia pytań (pytanie o termin, pułapki), odpowiedzi z egzaminów próbnych i nowe rodzaje
-- własnego testu. Zmiana nie dotyka istniejących danych: nowe tabele są puste, nowe kolumny mają wartości domyślne.
-- Oznaczenia wczytuje automat (POST /api/admin/question-marks/analyze) i/lub skrypt SQL (katalog baza/, skrypt 09).
-- ======================================================================================================

-- Oznaczenie pytania. Użytkownik nigdy ich nie widzi: służą tylko do układania testów „Pytania z terminów" i „Pułapki".
--   mark   : TIME_LIMIT (w pytaniu pada termin, np. do kiedy wnieść kasację; odpowiedzi różnią się terminami),
--            TRAP_NEGATION, TRAP_ABSOLUTE, TRAP_NUMBERS, TRAP_CONJUNCTION, TRAP_OUTDATED_LAW (podtypy pułapek).
--   origin : AUTO (zaproponował automat) albo MANUAL (nadał administrator).
--   status : SUGGESTED (czeka na przejrzenie), CONFIRMED (zatwierdzone, tylko takie liczą się w testach), REJECTED.
-- Pytanie ma najwyżej jeden wiersz na oznaczenie, więc odrzucona propozycja nie wraca po ponownej analizie.
CREATE TABLE question_marks (
    id          BIGSERIAL PRIMARY KEY,
    question_id BIGINT      NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    mark        VARCHAR(20) NOT NULL,
    origin      VARCHAR(6)  NOT NULL,
    status      VARCHAR(9)  NOT NULL,
    created_at  TIMESTAMP   NOT NULL,
    reviewed_at TIMESTAMP,
    reviewed_by BIGINT REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT uq_question_marks_question_mark UNIQUE (question_id, mark),
    CONSTRAINT chk_question_marks_mark CHECK (mark IN (
        'TIME_LIMIT', 'TRAP_NEGATION', 'TRAP_ABSOLUTE', 'TRAP_NUMBERS', 'TRAP_CONJUNCTION', 'TRAP_OUTDATED_LAW')),
    CONSTRAINT chk_question_marks_origin CHECK (origin IN ('AUTO', 'MANUAL')),
    CONSTRAINT chk_question_marks_status CHECK (status IN ('SUGGESTED', 'CONFIRMED', 'REJECTED')),
    -- Propozycja nie ma daty przejrzenia, każda decyzja ma; propozycje składa tylko automat.
    CONSTRAINT chk_question_marks_review CHECK (
        (status = 'SUGGESTED' AND reviewed_at IS NULL AND origin = 'AUTO')
        OR (status <> 'SUGGESTED' AND reviewed_at IS NOT NULL))
);
CREATE INDEX idx_question_marks_mark_status ON question_marks (mark, status);

-- Własny test może składać się z pytań o terminy albo z pułapek.
ALTER TABLE custom_test_sessions DROP CONSTRAINT chk_custom_test_sessions_kind;
ALTER TABLE custom_test_sessions ADD CONSTRAINT chk_custom_test_sessions_kind
    CHECK (kind IN ('ALL', 'NEW', 'MISTAKES', 'DUE', 'LAW_CHANGE', 'TIME_LIMIT', 'TRAPS'));

-- Egzamin próbny zapisuje teraz odpowiedzi, żeby można było wrócić do raportu w „Moich testach". Starsze podejścia
-- mają has_answers = false i zostają na liście tylko z wynikiem.
--   excluded_count : pytania z przesłanej listy, których nie dało się ocenić (usunięte albo niedostępne).
ALTER TABLE exam_attempts ADD COLUMN excluded_count INT NOT NULL DEFAULT 0;
ALTER TABLE exam_attempts ADD COLUMN has_answers BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE exam_attempts ADD CONSTRAINT chk_exam_attempts_excluded CHECK (excluded_count >= 0);

-- Odpowiedzi z jednego podejścia, w kolejności egzaminu (position od 1). selected_option NULL = bez odpowiedzi.
-- Skasowanie podejścia (zerowanie postępów, usunięcie konta) kasuje jego odpowiedzi.
CREATE TABLE exam_attempt_answers (
    id              BIGSERIAL PRIMARY KEY,
    attempt_id      BIGINT  NOT NULL REFERENCES exam_attempts (id) ON DELETE CASCADE,
    question_id     BIGINT  NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    "position"      INT     NOT NULL,
    selected_option VARCHAR(1),
    CONSTRAINT uq_exam_attempt_answers_position UNIQUE (attempt_id, "position"),
    CONSTRAINT uq_exam_attempt_answers_question UNIQUE (attempt_id, question_id),
    CONSTRAINT chk_exam_attempt_answers_position CHECK ("position" >= 1),
    CONSTRAINT chk_exam_attempt_answers_option CHECK (selected_option IS NULL OR selected_option IN ('A', 'B', 'C'))
);
CREATE INDEX idx_exam_attempt_answers_question ON exam_attempt_answers (question_id);
