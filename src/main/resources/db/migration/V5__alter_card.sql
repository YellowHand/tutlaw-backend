ALTER TABLE cards
    ADD COLUMN option_a TEXT;
ALTER TABLE cards
    ADD COLUMN option_b TEXT;
ALTER TABLE cards
    ADD COLUMN option_c TEXT;
ALTER TABLE cards
    ADD COLUMN correct_option VARCHAR(1);
ALTER TABLE cards
    ADD COLUMN explanation TEXT;