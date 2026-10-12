-- Reset postępów nauki (stan fiszek + historia powtórek).
--
-- KIEDY: po zmianie algorytmu na FSRS-6 (etap 1) stare terminy i stabilności z poprzedniej wersji
--        wzorów nie pasują do nowych. Na środowisku testowym najprościej zacząć od zera.
-- UWAGA: operacja jest NIEODWRACALNA. Nie uruchamia się sama, żadna migracja jej nie wywołuje.
--        Zrób kopię (pg_dump), jeśli są tam dane, których szkoda.
--
-- Uruchomienie:  psql -d tutlaw -f docs/reset-study-progress.sql

BEGIN;

-- Wariant A: wszyscy użytkownicy.
DELETE FROM review_logs;
DELETE FROM user_card_state;

-- Wariant B: tylko jeden użytkownik (odkomentuj, a wariant A zakomentuj).
-- DELETE FROM review_logs
--  WHERE user_id = (SELECT id FROM users WHERE private_email = 'ty@example.com');
-- DELETE FROM user_card_state
--  WHERE user_id = (SELECT id FROM users WHERE private_email = 'ty@example.com');

-- Oznaczenia „przeczytane" w rozdziałach podręczników zostają. Jeśli chcesz je też wyzerować:
-- DELETE FROM user_chapter_progress;

COMMIT;
