-- Etap 29 v2: terminy egzaminów ustawia administrator, a użytkownik wybiera aplikację, do której się przygotowuje.
-- Własna data egzaminu użytkownika znika (kolumna user_settings.exam_date jest usuwana razem z zapisanymi datami).
--
-- 1) exam_terms: jeden wiersz na aplikację (bez pytań autorskich), z datą najbliższego egzaminu albo NULL
--    ("termin nieogłoszony"). Wiersze są założone od razu, żeby administrator tylko ustawiał daty.
CREATE TABLE exam_terms (
    origin     VARCHAR(40) PRIMARY KEY,
    exam_date  DATE,
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_exam_terms_origin
        CHECK (origin IN ('ADVOCATE_LEGAL_ADVISER', 'JUDGE_PROSECUTOR', 'NOTARY', 'SUPPLEMENTARY_JUDGE', 'BAILIFF'))
);

INSERT INTO exam_terms (origin) VALUES
    ('ADVOCATE_LEGAL_ADVISER'), ('JUDGE_PROSECUTOR'), ('NOTARY'), ('SUPPLEMENTARY_JUDGE'), ('BAILIFF');

-- 2) Wybrana przez użytkownika aplikacja (NULL = jeszcze nie wybrał). Data egzaminu i odliczanie biorą się z exam_terms.
ALTER TABLE user_settings ADD COLUMN exam_origin VARCHAR(40);
ALTER TABLE user_settings
    ADD CONSTRAINT chk_user_settings_exam_origin
        CHECK (exam_origin IS NULL
            OR exam_origin IN ('ADVOCATE_LEGAL_ADVISER', 'JUDGE_PROSECUTOR', 'NOTARY', 'SUPPLEMENTARY_JUDGE', 'BAILIFF'));

-- 3) Własna data egzaminu użytkownika nie istnieje już w żadnej postaci.
ALTER TABLE user_settings DROP COLUMN exam_date;
