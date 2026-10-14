-- Etap 30: wygląd aplikacji (ciemny / jasny) zapisany na koncie, żeby był taki sam na każdym urządzeniu.
-- Istniejące konta dostają wygląd ciemny, czyli taki, jaki mają teraz.
ALTER TABLE user_settings ADD COLUMN theme VARCHAR(10) NOT NULL DEFAULT 'DARK';
ALTER TABLE user_settings
    ADD CONSTRAINT chk_user_settings_theme CHECK (theme IN ('DARK', 'LIGHT'));
