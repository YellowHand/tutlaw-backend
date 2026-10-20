-- ======================================================================================================
-- Etap 33: zmiany w prawie, własny test „Zmiany w prawie" i przedmioty wybierane przez studenta.
-- Zmiana nie dotyka istniejących danych: nowe kolumny są puste, nowa tabela jest pusta.
-- ======================================================================================================

-- Zmiana w prawie dotykająca pytania. Data to dzień wejścia zmiany w życie (albo dzień, w którym zaktualizowano
-- treść pytania, jeśli to później): od tego dnia pytanie wraca jako zaległe u każdego, kto odpowiadał na nie wcześniej,
-- a przez rok od tej daty trafia do kategorii „Zmiany w prawie". Data z przyszłości nic jeszcze nie zmienia.
-- Opis jest krótką wskazówką pokazywaną przy pytaniu (np. „nowelizacja art. 415 k.c. od 1.10.2026") i wymaga daty.
ALTER TABLE questions ADD COLUMN law_changed_at DATE;
ALTER TABLE questions ADD COLUMN law_change_note VARCHAR(500);
ALTER TABLE questions ADD CONSTRAINT chk_questions_law_change_note
    CHECK (law_change_note IS NULL OR (law_changed_at IS NOT NULL AND length(btrim(law_change_note)) > 0));
CREATE INDEX idx_questions_law_changed ON questions (law_changed_at) WHERE law_changed_at IS NOT NULL;

-- Własny test może składać się z pytań ze zmianą w prawie z ostatniego roku.
ALTER TABLE custom_test_sessions DROP CONSTRAINT chk_custom_test_sessions_kind;
ALTER TABLE custom_test_sessions ADD CONSTRAINT chk_custom_test_sessions_kind
    CHECK (kind IN ('ALL', 'NEW', 'MISTAKES', 'DUE', 'LAW_CHANGE'));

-- Student układa test z przedmiotów: numery wybranych przedmiotów oddzielone przecinkami (puste: bez przedmiotów).
-- Akty tych przedmiotów trafiają dodatkowo do act_ids, więc test da się ułożyć ponownie także po zmianie przedmiotu.
ALTER TABLE custom_test_sessions ADD COLUMN course_ids VARCHAR(500) NOT NULL DEFAULT '';

-- Przedmioty wybrane przez studenta (np. Prawo karne). Pulą pytań studenta są akty wybranych przedmiotów; brak wpisów
-- oznacza wszystkie przedmioty. Wpisy zostają przy zmianie ścieżki, żeby powrót na ścieżkę Student był bez ponownego wyboru.
CREATE TABLE user_courses (
    id        BIGSERIAL PRIMARY KEY,
    user_id   BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    course_id BIGINT NOT NULL REFERENCES courses (id) ON DELETE CASCADE,
    CONSTRAINT uq_user_courses UNIQUE (user_id, course_id)
);
CREATE INDEX idx_user_courses_course ON user_courses (course_id);
