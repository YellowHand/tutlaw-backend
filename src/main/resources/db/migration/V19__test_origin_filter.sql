-- Etap 18: filtr pochodzenia pytań w Testach (zapisany na koncie użytkownika).
--
-- test_origins to lista wybranych pochodzeń oddzielona przecinkami, np. 'NOTARY,BAILIFF'. NULL oznacza "wszystkie"
-- (wartość domyślna: nikt, kto niczego nie ustawił, nie traci pytań, a nowe pochodzenia dodane w przyszłości
-- są od razu widoczne). Ograniczenie pilnuje, że lista nie jest pusta i zawiera tylko znane kody.
-- Filtr dotyczy tylko Testów (katalog, kolejki, odznaka, błędy, egzamin). Fiszki i statystyki go nie widzą.
ALTER TABLE user_settings ADD COLUMN test_origins VARCHAR(200);
ALTER TABLE user_settings
    ADD CONSTRAINT chk_user_settings_test_origins
        CHECK (test_origins IS NULL OR test_origins ~
            '^(AUTHORED|ADVOCATE_LEGAL_ADVISER|JUDGE_PROSECUTOR|NOTARY|SUPPLEMENTARY_JUDGE|BAILIFF)(,(AUTHORED|ADVOCATE_LEGAL_ADVISER|JUDGE_PROSECUTOR|NOTARY|SUPPLEMENTARY_JUDGE|BAILIFF))*$');
