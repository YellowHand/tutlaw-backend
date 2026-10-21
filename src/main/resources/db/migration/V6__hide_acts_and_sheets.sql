-- ======================================================================================================
-- Etap 35: ukrywanie aktów prawnych i arkuszy egzaminacyjnych (panel administratora).
-- Akt albo arkusz, który ma pytania, nie może zostać usunięty, można go tylko ukryć: ukryty znika wszystkim
-- użytkownikom (testy, fiszki, liczniki, egzamin, Egzaminy MS), a pytania i historia odpowiedzi zostają.
-- Zmiana nie dotyka istniejących danych: nowe kolumny mają wartość domyślną false (nic nie jest ukryte).
-- ======================================================================================================

ALTER TABLE legal_acts ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE question_sources ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT false;
