-- Skąd pochodzi odpowiedź (fiszka / test) oraz co faktycznie się stało.
-- Dzięki temu wynik testu (poprawne odpowiedzi) da się liczyć osobno od oceny pamięci (Znam / Nie pamiętam),
-- a dzienny limit nowych pytań może być osobny dla fiszek i testów.
ALTER TABLE review_logs
    ADD COLUMN mode VARCHAR(20) NOT NULL DEFAULT 'FLASHCARD';
ALTER TABLE review_logs
    ADD COLUMN was_correct BOOLEAN;
ALTER TABLE review_logs
    ADD COLUMN was_guessed BOOLEAN NOT NULL DEFAULT false;
