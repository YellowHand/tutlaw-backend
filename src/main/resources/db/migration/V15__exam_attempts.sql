-- Etap 10: egzamin próbny. Zapisujemy tylko podsumowanie podejścia (zakres, wynik, czas, próg), bez pojedynczych
-- odpowiedzi: szczegółowy raport użytkownik dostaje od razu po zakończeniu egzaminu, a nauka (FSRS, dziennik
-- odpowiedzi) egzaminu próbnego w ogóle nie dotyczy. Podejścia znikają razem z kontem (ON DELETE CASCADE).
CREATE TABLE exam_attempts (
    id                     BIGSERIAL PRIMARY KEY,
    user_id                BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    finished_at            TIMESTAMP    NOT NULL,
    scope_label            VARCHAR(300) NOT NULL,
    question_count         INTEGER      NOT NULL,
    answered_count         INTEGER      NOT NULL,
    correct_count          INTEGER      NOT NULL,
    pass_threshold_percent INTEGER      NOT NULL,
    passed                 BOOLEAN      NOT NULL,
    time_limit_minutes     INTEGER,
    duration_seconds       INTEGER      NOT NULL,
    timed_out              BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_exam_attempts_counts CHECK (
        question_count BETWEEN 1 AND 200
        AND answered_count BETWEEN 0 AND question_count
        AND correct_count BETWEEN 0 AND answered_count),
    CONSTRAINT chk_exam_attempts_threshold CHECK (pass_threshold_percent BETWEEN 1 AND 100),
    CONSTRAINT chk_exam_attempts_time_limit CHECK (time_limit_minutes IS NULL OR time_limit_minutes BETWEEN 1 AND 600),
    CONSTRAINT chk_exam_attempts_duration CHECK (duration_seconds BETWEEN 0 AND 172800)
);

-- Historia podejść użytkownika: od najnowszego.
CREATE INDEX idx_exam_attempts_user_finished ON exam_attempts (user_id, finished_at DESC);
