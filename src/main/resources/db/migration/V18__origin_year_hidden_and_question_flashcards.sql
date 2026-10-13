-- Etap 17: pochodzenie i rok pytania, ukrywanie kart, fiszki tworzone automatycznie z pytań.
--
-- 1) Pochodzenie (origin) i rok egzaminu (exam_year). Każda karta ma pochodzenie; domyślnie AUTHORED (pytanie
--    autorskie), czyli wszystkie dotychczasowe karty zostają autorskie. Rok jest wymagany dla pytań z aplikacji
--    i musi być pusty dla autorskich. Data utworzenia (created_at) już jest w tabeli i nie jest nigdzie pokazywana.
-- 2) Ukrywanie (hidden): ukryta karta znika wszystkim (testy, fiszki, liczniki, egzamin), historia zostaje.
--    Ustawiasz ręcznie: UPDATE cards SET hidden = true WHERE id = ...;
-- 3) Fiszka tworzona automatycznie z każdego pytania testowego (auto_generated). Utrzymuje ją wyzwalacz: dodanie,
--    zmiana i usunięcie pytania (także zwykłym SQL-em) dopisuje, aktualizuje albo usuwa jego fiszkę.
-- 4) Usunięcie karty usuwa też jej historię (odpowiedzi, stany nauki, etykiety). Wcześniej baza odmawiała usunięcia
--    pytania, na które ktoś już odpowiedział.
-- 5) Ręczne dodanie pytania do fiszek przez użytkownika (user_flashcard_additions).

-- 1) Pochodzenie i rok -----------------------------------------------------------------------------------------
ALTER TABLE cards ADD COLUMN origin VARCHAR(40) NOT NULL DEFAULT 'AUTHORED';
ALTER TABLE cards ADD COLUMN exam_year INTEGER;
ALTER TABLE cards
    ADD CONSTRAINT chk_cards_origin
        CHECK (origin IN ('AUTHORED', 'ADVOCATE_LEGAL_ADVISER', 'JUDGE_PROSECUTOR', 'NOTARY',
                          'SUPPLEMENTARY_JUDGE', 'BAILIFF'));
ALTER TABLE cards
    ADD CONSTRAINT chk_cards_exam_year
        CHECK ((origin = 'AUTHORED' AND exam_year IS NULL)
            OR (origin <> 'AUTHORED' AND exam_year IS NOT NULL AND exam_year BETWEEN 1990 AND 2100));
CREATE INDEX idx_cards_origin ON cards (origin);

-- 2) Ukrywanie ---------------------------------------------------------------------------------------------------
ALTER TABLE cards ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE;

-- 3) Fiszka z pytania --------------------------------------------------------------------------------------------
ALTER TABLE cards ADD COLUMN auto_generated BOOLEAN NOT NULL DEFAULT FALSE;
-- Automatyczna fiszka jest zawsze fiszką (bez opcji) powiązaną z pytaniem.
ALTER TABLE cards
    ADD CONSTRAINT chk_cards_auto_generated_flashcard
        CHECK (NOT auto_generated OR (correct_option IS NULL AND linked_test_card_id IS NOT NULL));

-- Tworzy, aktualizuje albo (gdy pytanie przestało być pytaniem) usuwa automatyczną fiszkę pytania.
--  * przód = treść pytania, tył = poprawna odpowiedź, a gdy pytanie ma wyjaśnienie, to po pustej linii także ono,
--  * podstawa prawna, data stanu prawnego, pochodzenie, rok, źródło i rozdział są kopiowane z pytania
--    (fiszka z pytania z podręcznika zależy od przeczytania tego samego rozdziału),
--  * jeśli pytanie ma już fiszkę napisaną ręcznie (powiązaną, a nie automatyczną), automatycznej nie tworzymy,
--    a dodanie takiej fiszki do pytania, które ma już automatyczną, tę automatyczną usuwa,
--  * ukrycie pytania ukrywa jego automatyczną fiszkę, a odkrycie pytania ją odkrywa. Gdy ukrycie pytania się
--    nie zmienia, fiszka zachowuje własny stan ukrycia (możesz ukryć samą fiszkę).
CREATE OR REPLACE FUNCTION sync_question_flashcard() RETURNS trigger
    LANGUAGE plpgsql AS
$$
DECLARE
    answer      TEXT;
    new_back    TEXT;
    auto_id     BIGINT;
    hide_change BOOLEAN;
BEGIN
    IF NEW.correct_option IS NULL THEN
        -- To nie jest pytanie. Jeśli kiedyś nim było, jego automatyczna fiszka traci sens.
        IF TG_OP = 'UPDATE' AND OLD.correct_option IS NOT NULL THEN
            DELETE FROM cards WHERE linked_test_card_id = NEW.id AND auto_generated;
        END IF;
        -- Fiszka napisana ręcznie i powiązana z pytaniem wygrywa: automatyczna fiszka tego pytania jest zbędna.
        IF NEW.linked_test_card_id IS NOT NULL AND NOT NEW.auto_generated THEN
            DELETE FROM cards WHERE linked_test_card_id = NEW.linked_test_card_id AND auto_generated AND id <> NEW.id;
        END IF;
        RETURN NULL;
    END IF;

    answer := CASE NEW.correct_option
                  WHEN 'A' THEN NEW.option_a
                  WHEN 'B' THEN NEW.option_b
                  ELSE NEW.option_c
        END;
    new_back := answer;
    IF NEW.explanation IS NOT NULL AND btrim(NEW.explanation) <> '' THEN
        new_back := answer || E'\n\n' || NEW.explanation;
    END IF;

    SELECT id INTO auto_id FROM cards WHERE linked_test_card_id = NEW.id AND auto_generated LIMIT 1;

    IF auto_id IS NOT NULL THEN
        hide_change := TG_OP = 'UPDATE' AND OLD.hidden IS DISTINCT FROM NEW.hidden;
        UPDATE cards
        SET front       = NEW.front,
            back        = new_back,
            legal_basis = NEW.legal_basis,
            valid_as_of = NEW.valid_as_of,
            source_id   = NEW.source_id,
            chapter_id  = NEW.chapter_id,
            origin      = NEW.origin,
            exam_year   = NEW.exam_year,
            hidden      = CASE WHEN hide_change THEN NEW.hidden ELSE hidden END
        WHERE id = auto_id
          AND (front, back, legal_basis, valid_as_of, source_id, chapter_id, origin, exam_year, hidden)
            IS DISTINCT FROM
              (NEW.front, new_back, NEW.legal_basis, NEW.valid_as_of, NEW.source_id, NEW.chapter_id, NEW.origin,
               NEW.exam_year, CASE WHEN hide_change THEN NEW.hidden ELSE hidden END);
    ELSIF NOT EXISTS (SELECT 1 FROM cards WHERE linked_test_card_id = NEW.id) THEN
        INSERT INTO cards (source_id, chapter_id, type, front, back, legal_basis, valid_as_of, origin, exam_year,
                           hidden, linked_test_card_id, auto_generated)
        VALUES (NEW.source_id, NEW.chapter_id, 'DEFINITION', NEW.front, new_back, NEW.legal_basis, NEW.valid_as_of,
                NEW.origin, NEW.exam_year, NEW.hidden, NEW.id, TRUE);
    END IF;
    RETURN NULL;
END
$$;

CREATE TRIGGER trg_cards_sync_question_flashcard
    AFTER INSERT OR UPDATE
    ON cards
    FOR EACH ROW
EXECUTE FUNCTION sync_question_flashcard();

-- Usunięcie pytania usuwa jego automatyczną fiszkę. Robimy to PRZED usunięciem pytania, bo po nim klucz obcy
-- fk_cards_linked_test_card odpiąłby fiszkę (linked_test_card_id = NULL) i nie wiedzielibyśmy już, której dotyczyła.
CREATE OR REPLACE FUNCTION delete_question_flashcard() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF OLD.correct_option IS NOT NULL THEN
        DELETE FROM cards WHERE linked_test_card_id = OLD.id AND auto_generated;
    END IF;
    RETURN OLD;
END
$$;

CREATE TRIGGER trg_cards_delete_question_flashcard
    BEFORE DELETE
    ON cards
    FOR EACH ROW
EXECUTE FUNCTION delete_question_flashcard();

-- Usunięcie ręcznej fiszki powiązanej z pytaniem przywraca fiszkę automatyczną tego pytania (dotknięcie pytania
-- uruchamia wyzwalacz tworzący). Gdy pytanie jest usuwane razem z fiszką, nie ma czego przywracać.
CREATE OR REPLACE FUNCTION restore_question_flashcard() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    UPDATE cards SET front = front WHERE id = OLD.linked_test_card_id AND correct_option IS NOT NULL;
    RETURN NULL;
END
$$;

CREATE TRIGGER trg_cards_restore_question_flashcard
    AFTER DELETE
    ON cards
    FOR EACH ROW
    WHEN (OLD.correct_option IS NULL AND OLD.linked_test_card_id IS NOT NULL AND NOT OLD.auto_generated)
EXECUTE FUNCTION restore_question_flashcard();

-- 4) Usunięcie karty usuwa jej historię ----------------------------------------------------------------------------
ALTER TABLE review_logs DROP CONSTRAINT review_logs_card_id_fkey;
ALTER TABLE review_logs
    ADD CONSTRAINT review_logs_card_id_fkey FOREIGN KEY (card_id) REFERENCES cards (id) ON DELETE CASCADE;
ALTER TABLE user_card_state DROP CONSTRAINT user_card_state_card_id_fkey;
ALTER TABLE user_card_state
    ADD CONSTRAINT user_card_state_card_id_fkey FOREIGN KEY (card_id) REFERENCES cards (id) ON DELETE CASCADE;
ALTER TABLE card_tags DROP CONSTRAINT card_tags_card_id_fkey;
ALTER TABLE card_tags
    ADD CONSTRAINT card_tags_card_id_fkey FOREIGN KEY (card_id) REFERENCES cards (id) ON DELETE CASCADE;

-- 5) Ręczne dodanie pytania do fiszek ------------------------------------------------------------------------------
CREATE TABLE user_flashcard_additions
(
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    question_id BIGINT    NOT NULL REFERENCES cards (id) ON DELETE CASCADE,
    added_at    TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_flashcard_additions UNIQUE (user_id, question_id)
);
CREATE INDEX idx_user_flashcard_additions_question ON user_flashcard_additions (question_id);

-- Fiszki dla pytań, które już są w bazie: ten sam wyzwalacz, bez powielania logiki (zmiana bez zmiany wartości
-- też uruchamia wyzwalacz dla każdego wiersza).
UPDATE cards
SET front = front
WHERE correct_option IS NOT NULL;
