-- Etap 28: egzamin w stałym formacie (150 pytań, zaliczenie od 100 punktów). Próg ustala serwer i zapisuje go w
-- punktach. Starsze podejścia, które miały własny próg w procentach, zostają bez zmian: mają pass_points = NULL
-- i dalej pokazują próg z kolumny pass_threshold_percent.
ALTER TABLE exam_attempts ADD COLUMN pass_points INTEGER;

ALTER TABLE exam_attempts ADD CONSTRAINT chk_exam_attempts_pass_points
    CHECK (pass_points IS NULL OR pass_points BETWEEN 1 AND question_count);
