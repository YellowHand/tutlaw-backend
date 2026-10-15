-- Etap 31: czas nauki, ustawienia interfejsu i wybór startowy.

-- Czas nauki w sekundach, osobno dla każdego dnia (dzień wg czasu serwera). Liczy czas spędzony w aplikacji na nauce
-- (testy, powtórki, fiszki, egzamin, razem z czytaniem wyjaśnień), tylko gdy karta przeglądarki jest widoczna.
-- To inna miara niż czas odpowiadania na pytanie (review_logs.time_spent_seconds). Znika razem z kontem.
CREATE TABLE study_time_daily (
    user_id BIGINT  NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    day     DATE    NOT NULL,
    seconds INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, day),
    CONSTRAINT chk_study_time_daily_seconds CHECK (seconds BETWEEN 0 AND 86400)
);

-- Ustawienia interfejsu zapisane na koncie, żeby były takie same na każdym urządzeniu:
-- stałe ukrycie podpowiedzi o skrótach klawiszowych (osobno w testach i w fiszkach) i ukrycie licznika czasu pytania.
ALTER TABLE user_settings ADD COLUMN hint_hidden_test BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE user_settings ADD COLUMN hint_hidden_flashcard BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE user_settings ADD COLUMN question_timer_hidden BOOLEAN NOT NULL DEFAULT FALSE;

-- Czy użytkownik dokonał wyboru w oknie startowym ("Przygotowuję się do aplikacji ..." albo "Jestem studentem").
-- Student nie ma aplikacji (exam_origin zostaje puste), więc samo exam_origin nie mówi już, czy wybór został zrobiony.
-- Konta, które aplikację już wybrały, mają wybór zrobiony.
ALTER TABLE user_settings ADD COLUMN start_choice_made BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE user_settings SET start_choice_made = TRUE WHERE exam_origin IS NOT NULL;
