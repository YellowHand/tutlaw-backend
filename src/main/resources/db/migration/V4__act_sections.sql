-- ======================================================================================================
-- Etap 34: rozdziały aktów prawnych (np. „Rozdział XIX. Przestępstwa przeciwko życiu i zdrowiu" w Kodeksie karnym).
-- Zmiana nie dotyka istniejących danych: nowa tabela jest pusta, a nowa kolumna w questions ma wartość NULL.
-- Rozdziały i przypisanie do nich pytań wczytuje się skryptem SQL (katalog baza/, skrypt 07).
-- ======================================================================================================

-- Rozdział (dział, tytuł) jednego aktu prawnego. Nazwa zawiera numer rozdziału, tak jak ma się pokazać użytkownikowi.
-- Akt może nie mieć żadnych rozdziałów (wtedy w Testach wybiera się go tylko w całości).
CREATE TABLE act_sections (
    id           BIGSERIAL PRIMARY KEY,
    legal_act_id BIGINT       NOT NULL REFERENCES legal_acts (id) ON DELETE CASCADE,
    name         VARCHAR(300) NOT NULL,
    sort_order   INT          NOT NULL DEFAULT 100,
    CONSTRAINT uq_act_sections_name UNIQUE (legal_act_id, name),
    CONSTRAINT chk_act_sections_name_not_blank CHECK (length(btrim(name)) > 0),
    CONSTRAINT chk_act_sections_sort_order CHECK (sort_order >= 0)
);
CREATE INDEX idx_act_sections_act ON act_sections (legal_act_id);

-- Rozdział, do którego należy pytanie. NULL: pytanie nie ma rozdziału (jest tylko w akcie jako całości). Skasowanie
-- rozdziału nie kasuje pytań, tylko zdejmuje z nich przypisanie.
ALTER TABLE questions ADD COLUMN section_id BIGINT REFERENCES act_sections (id) ON DELETE SET NULL;
CREATE INDEX idx_questions_section ON questions (section_id) WHERE section_id IS NOT NULL;

-- Własny test może być zawężony do rozdziałów: numery wybranych rozdziałów oddzielone przecinkami (puste: bez rozdziałów).
-- Dzięki temu „Moje testy" potrafią ułożyć taki test ponownie.
ALTER TABLE custom_test_sessions ADD COLUMN section_ids VARCHAR(2000) NOT NULL DEFAULT '';
