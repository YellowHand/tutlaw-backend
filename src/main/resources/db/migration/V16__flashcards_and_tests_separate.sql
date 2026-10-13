-- Etap 14: Fiszki i Testy to osobne światy. Karta z opcjami A/B/C (pytanie testowe) jest tylko w Testach,
-- karta bez opcji tylko w Fiszkach. Z tego wynikają dwie zmiany w tabeli cards:
--
-- 1) Pole back (odpowiedź na odwrocie fiszki) jest wymagane tylko dla fiszki. Pytanie testowe ma poprawną
--    odpowiedź wskazaną w correct_option, więc back byłoby jej dubletem (i mogłoby jej przeczyć).
-- 2) Fiszka może być powiązana z pytaniem testowym (linked_test_card_id): wtedy nie pokazuje się w Fiszkach,
--    dopóki użytkownik nie pomyli tego pytania w teście bez zaznaczenia „Nie jestem pewien".

ALTER TABLE cards ALTER COLUMN back DROP NOT NULL;

-- Zastępuje ograniczenie z V8 (front i back niepuste). Teraz: front zawsze niepusty, back albo NULL, albo niepusty,
-- a fiszka (bez correct_option) musi mieć back. NOT VALID jak w V8: pilnuje nowych i zmienianych wierszy,
-- nie sprawdza wstecz starych danych (VALIDATE CONSTRAINT możesz uruchomić, gdy będziesz pewien, że są czyste).
ALTER TABLE cards DROP CONSTRAINT IF EXISTS chk_cards_text_not_blank;
ALTER TABLE cards
    ADD CONSTRAINT chk_cards_text_not_blank
        CHECK (length(btrim(front)) > 0
            AND (back IS NULL OR length(btrim(back)) > 0)
            AND (correct_option IS NOT NULL OR back IS NOT NULL)) NOT VALID;

-- Powiązana fiszka. Usunięcie pytania testowego nie usuwa fiszki, tylko odpina ją (zostaje zwykłą fiszką).
ALTER TABLE cards ADD COLUMN linked_test_card_id BIGINT;
ALTER TABLE cards
    ADD CONSTRAINT fk_cards_linked_test_card
        FOREIGN KEY (linked_test_card_id) REFERENCES cards (id) ON DELETE SET NULL;
-- Powiązanie ma tylko fiszka (karta bez opcji), nigdy samo pytanie testowe i nigdy samo ze sobą.
ALTER TABLE cards
    ADD CONSTRAINT chk_cards_linked_only_on_flashcard
        CHECK (linked_test_card_id IS NULL OR correct_option IS NULL);
ALTER TABLE cards
    ADD CONSTRAINT chk_cards_not_linked_to_self
        CHECK (linked_test_card_id IS NULL OR linked_test_card_id <> id);
CREATE INDEX idx_cards_linked_test_card ON cards (linked_test_card_id) WHERE linked_test_card_id IS NOT NULL;
