-- Reset postępów nauki: stan nauki pytań (osobno testy i fiszki), historia odpowiedzi, ręczne dodania do fiszek,
-- zapisane własne testy, egzaminy próbne i czas nauki. Konta, ustawienia, wybrana ścieżka, zawężenie puli,
-- wybrane przedmioty Studenta (user_courses), zgłoszenia błędów i cała treść (pytania, akty, arkusze, zmiany w prawie)
-- zostają. To ten sam zakres, który użytkownik zeruje sam w aplikacji (Konto → „Wyzeruj postępy”, słowo WYZERUJ).
--
-- KIEDY: na środowisku testowym, gdy chcesz zacząć naukę od zera, albo po zmianie algorytmu powtórek.
-- UWAGA: operacja jest NIEODWRACALNA. Nie uruchamia się sama, żadna migracja jej nie wywołuje.
--        Zrób kopię (pg_dump), jeśli są tam dane, których szkoda.
--
-- Uruchomienie:  psql -d tutlaw -f docs/reset-study-progress.sql

BEGIN;

-- Wariant A: wszyscy użytkownicy.
DELETE FROM custom_test_sessions;      -- razem z custom_test_session_questions (ON DELETE CASCADE)
DELETE FROM flashcard_additions;
DELETE FROM review_logs;
DELETE FROM user_question_states;
DELETE FROM exam_attempts;
DELETE FROM study_time_daily;

-- Wariant B: tylko jeden użytkownik (odkomentuj, a wariant A zakomentuj).
-- DELETE FROM custom_test_sessions  WHERE user_id = (SELECT id FROM users WHERE private_email = 'ty@example.com');
-- DELETE FROM flashcard_additions   WHERE user_id = (SELECT id FROM users WHERE private_email = 'ty@example.com');
-- DELETE FROM review_logs           WHERE user_id = (SELECT id FROM users WHERE private_email = 'ty@example.com');
-- DELETE FROM user_question_states  WHERE user_id = (SELECT id FROM users WHERE private_email = 'ty@example.com');
-- DELETE FROM exam_attempts         WHERE user_id = (SELECT id FROM users WHERE private_email = 'ty@example.com');
-- DELETE FROM study_time_daily      WHERE user_id = (SELECT id FROM users WHERE private_email = 'ty@example.com');

-- Uwaga: usunięcie review_logs i flashcard_additions oznacza też, że żadna fiszka nie jest już odblokowana
-- (fiszkę odblokowuje pewny błąd w teście albo ręczne dodanie).

COMMIT;
