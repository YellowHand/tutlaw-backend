-- Etap 5B: usuwanie konta (RODO).
-- Dotąd klucze obce do users(id) nie miały ON DELETE CASCADE, więc użytkownika z jakimikolwiek danymi nauki
-- nie dało się usunąć. Teraz usunięcie wiersza z users zabiera też jego postępy, historię odpowiedzi,
-- stan kart i tokeny. Nazwy ograniczeń odczytujemy z bazy, więc migracja działa niezależnie od tego,
-- jak je nazwano wcześniej. Tabela password_reset_tokens (V10) ma już CASCADE i zostaje bez zmian.
DO $$
DECLARE
    fk RECORD;
BEGIN
    FOR fk IN
        SELECT c.conrelid::regclass AS table_name, c.conname AS constraint_name, a.attname AS column_name
        FROM pg_constraint c
                 JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]
        WHERE c.contype = 'f'
          AND c.confrelid = 'users'::regclass
          AND c.confdeltype <> 'c'
          AND array_length(c.conkey, 1) = 1
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', fk.table_name, fk.constraint_name);
        EXECUTE format('ALTER TABLE %s ADD CONSTRAINT %I FOREIGN KEY (%I) REFERENCES users (id) ON DELETE CASCADE',
                       fk.table_name, fk.constraint_name, fk.column_name);
    END LOOP;
END
$$;
