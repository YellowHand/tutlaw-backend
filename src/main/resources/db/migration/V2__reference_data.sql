-- Dane słownikowe, bez których aplikacja nie ruszy: dziedziny, ścieżki, źródło „pytania autorskie" i puste terminy
-- egzaminów. Akty prawne, listy aktów ścieżek, arkusze i pytania wczytuje się skryptami z katalogu baza/.

INSERT INTO legal_fields (name, slug, sort_order) VALUES
    ('Prawo cywilne i postępowanie cywilne',        'prawo-cywilne',          10),
    ('Prawo karne i proces karny',                  'prawo-karne',            20),
    ('Administracja i postępowanie administracyjne', 'administracja',          30),
    ('Prawo handlowe i spółek',                     'prawo-handlowe',         40),
    ('Prawo pracy i zabezpieczenia społecznego',    'prawo-pracy',            50),
    ('Konstytucja i ustrój państwa',                'konstytucja-i-ustroj',   60),
    ('Zawody prawnicze i ustrój sądów',             'zawody-i-ustroj-sadow',  70),
    ('Prawo Unii Europejskiej i międzynarodowe',    'prawo-ue-i-miedzynarodowe', 80),
    ('Podatki i opłaty',                            'podatki-i-oplaty',       90),
    ('Student',                                     'student',               100),
    ('Pozostałe',                                   'pozostale',             110);

INSERT INTO tracks (slug, name, short_name, kind, sort_order) VALUES
    ('adwokacka-radcowska',      'Aplikacja adwokacka i radcowska',  'Adwokacka i radcowska',   'EXAM',    10),
    ('sedziowska-prokuratorska', 'Aplikacja sędziowska i prokuratorska', 'Sędziowska i prokuratorska', 'EXAM', 20),
    ('uzupelniajaca-sedziowska', 'Aplikacja uzupełniająca sędziowska', 'Uzupełniająca sędziowska', 'EXAM',  30),
    ('notarialna',               'Aplikacja notarialna',             'Notarialna',              'EXAM',    40),
    ('komornicza',               'Aplikacja komornicza',             'Komornicza',              'EXAM',    50),
    ('student',                  'Student',                          'Student',                 'STUDENT', 60);

-- Terminy egzaminów (administrator ustawia datę; NULL = nieogłoszony). Wiersz dla każdej aplikacji.
INSERT INTO exam_terms (track_id)
SELECT id FROM tracks WHERE kind = 'EXAM';

-- Źródło dla pytań autorskich (jedno; nie należy do żadnej ścieżki).
INSERT INTO question_sources (slug, kind, name) VALUES ('autorskie', 'AUTHORED', 'Pytania autorskie');
