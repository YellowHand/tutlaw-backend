-- Tutlaw: schemat bazy (stan wyjściowy po przebudowie „aplikacje + Student").
--
-- Układ w skrócie:
--   dziedzina (legal_fields) -> akt prawny / zagadnienie (legal_acts) -> pytanie (questions)
--   ścieżka (tracks): pięć aplikacji z własnymi arkuszami i listą aktów + Student z przedmiotami (courses)
--   źródło pytania (question_sources): arkusz egzaminacyjny ścieżki albo pytania autorskie
--   pula użytkownika = pytania z jego arkuszy LUB z aktów jego ścieżki (reguła, nie ręczne przypisania)
--   fiszka nie jest osobnym rekordem: to tryb nauki pytania (user_question_states.mode = FLASHCARD)
--
-- Dane słownikowe (dziedziny, ścieżki) są w V2. Akty prawne, arkusze i pytania wczytuje się skryptami SQL z katalogu baza/.

-- ======================================================================================================
-- Konta i tokeny
-- ======================================================================================================

CREATE TABLE users (
    id                           BIGSERIAL PRIMARY KEY,
    private_email                VARCHAR(255) NOT NULL UNIQUE,
    university_email             VARCHAR(255) NOT NULL UNIQUE,
    password_hash                VARCHAR(255) NOT NULL,
    is_university_email_verified BOOLEAN      NOT NULL DEFAULT false,
    is_active                    BOOLEAN      NOT NULL DEFAULT false,
    role                         VARCHAR(20)  NOT NULL DEFAULT 'STUDENT',
    created_at                   TIMESTAMP    NOT NULL DEFAULT now(),
    terms_accepted_at            TIMESTAMP,
    tokens_valid_from            BIGINT,
    CONSTRAINT chk_users_role CHECK (role IN ('ADMIN', 'STUDENT'))
);

CREATE TABLE email_verification_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token      VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP    NOT NULL,
    used_at    TIMESTAMP,
    created_at TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_email_verification_tokens_user_id ON email_verification_tokens (user_id);

CREATE TABLE password_reset_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP   NOT NULL,
    used_at    TIMESTAMP,
    created_at TIMESTAMP   NOT NULL DEFAULT now()
);
CREATE INDEX idx_password_reset_tokens_user_created ON password_reset_tokens (user_id, created_at DESC);

CREATE TABLE refresh_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    family_id  VARCHAR(36) NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT now(),
    expires_at TIMESTAMP   NOT NULL,
    rotated_at TIMESTAMP,
    revoked_at TIMESTAMP
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);

-- ======================================================================================================
-- Katalog: dziedziny, akty prawne, ścieżki, przedmioty studenta, źródła pytań
-- ======================================================================================================

-- Dziedzina to duży dział prawa. Widzą ją wszyscy użytkownicy; aktów w niej widzą tylko te ze swojej puli.
CREATE TABLE legal_fields (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    slug       VARCHAR(100) NOT NULL UNIQUE,
    sort_order INT          NOT NULL DEFAULT 100,
    CONSTRAINT chk_legal_fields_sort_order CHECK (sort_order >= 0)
);

-- Akt prawny (kodeks, ustawa) albo zagadnienie (ACT / TOPIC). Zagadnienie to materiał, który nie jest ustawą, np.
-- prawoznawstwo, historia prawa, paremie łacińskie; pytania do niego przypisuje się bezpośrednio.
CREATE TABLE legal_acts (
    id             BIGSERIAL PRIMARY KEY,
    legal_field_id BIGINT       NOT NULL REFERENCES legal_fields (id),
    name           VARCHAR(300) NOT NULL,
    short_name     VARCHAR(50),
    slug           VARCHAR(100) NOT NULL UNIQUE,
    kind           VARCHAR(10)  NOT NULL DEFAULT 'ACT',
    sort_order     INT          NOT NULL DEFAULT 100,
    CONSTRAINT chk_legal_acts_kind CHECK (kind IN ('ACT', 'TOPIC')),
    CONSTRAINT chk_legal_acts_sort_order CHECK (sort_order >= 0)
);
CREATE INDEX idx_legal_acts_field ON legal_acts (legal_field_id);

-- Ścieżka: jedna z pięciu aplikacji (EXAM) albo Student (STUDENT). Aplikacja ma arkusze i listę aktów prawnych.
CREATE TABLE tracks (
    id         BIGSERIAL PRIMARY KEY,
    slug       VARCHAR(60)  NOT NULL UNIQUE,
    name       VARCHAR(255) NOT NULL,
    short_name VARCHAR(60)  NOT NULL,
    kind       VARCHAR(10)  NOT NULL,
    sort_order INT          NOT NULL DEFAULT 100,
    CONSTRAINT chk_tracks_kind CHECK (kind IN ('EXAM', 'STUDENT')),
    CONSTRAINT chk_tracks_sort_order CHECK (sort_order >= 0)
);
-- Ścieżka Student jest jedna.
CREATE UNIQUE INDEX uq_tracks_single_student ON tracks (kind) WHERE kind = 'STUDENT';

-- Akty prawne ścieżki-aplikacji (ustawia je administrator). Ścieżka Student nie ma tu wpisów: jej akty to akty
-- wszystkich przedmiotów (course_acts).
CREATE TABLE track_acts (
    track_id     BIGINT NOT NULL REFERENCES tracks (id) ON DELETE CASCADE,
    legal_act_id BIGINT NOT NULL REFERENCES legal_acts (id) ON DELETE CASCADE,
    PRIMARY KEY (track_id, legal_act_id)
);
CREATE INDEX idx_track_acts_act ON track_acts (legal_act_id);

-- Przedmiot studenta (np. Prawo karne, Prawoznawstwo, Paremie łacińskie) i jego akty / zagadnienia.
CREATE TABLE courses (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    slug       VARCHAR(100) NOT NULL UNIQUE,
    sort_order INT          NOT NULL DEFAULT 100,
    CONSTRAINT chk_courses_sort_order CHECK (sort_order >= 0)
);

CREATE TABLE course_acts (
    course_id    BIGINT NOT NULL REFERENCES courses (id) ON DELETE CASCADE,
    legal_act_id BIGINT NOT NULL REFERENCES legal_acts (id) ON DELETE CASCADE,
    PRIMARY KEY (course_id, legal_act_id)
);
CREATE INDEX idx_course_acts_act ON course_acts (legal_act_id);

-- Skąd pochodzi pytanie: arkusz egzaminu wstępnego danej ścieżki w danym roku (EXAM_SHEET) albo pytania autorskie
-- (AUTHORED). Arkusz „własny" użytkownika to arkusz jego ścieżki; arkusze innych ścieżek są „z innych arkuszy".
CREATE TABLE question_sources (
    id        BIGSERIAL PRIMARY KEY,
    slug      VARCHAR(100) NOT NULL UNIQUE,
    kind      VARCHAR(12)  NOT NULL,
    name      VARCHAR(255) NOT NULL,
    track_id  BIGINT REFERENCES tracks (id),
    exam_year INT,
    CONSTRAINT chk_question_sources_kind CHECK (kind IN ('EXAM_SHEET', 'AUTHORED')),
    CONSTRAINT chk_question_sources_shape CHECK (
        (kind = 'EXAM_SHEET' AND track_id IS NOT NULL AND exam_year BETWEEN 1990 AND 2100)
        OR (kind = 'AUTHORED' AND track_id IS NULL AND exam_year IS NULL))
);
-- Jeden arkusz na ścieżkę i rok.
CREATE UNIQUE INDEX uq_question_sources_sheet ON question_sources (track_id, exam_year) WHERE kind = 'EXAM_SHEET';
CREATE INDEX idx_question_sources_track ON question_sources (track_id);

-- ======================================================================================================
-- Pytania
-- ======================================================================================================

-- Pytanie ABC z jedną poprawną odpowiedzią. Tryb fiszki korzysta z tej samej treści: przód to treść pytania, tył to
-- poprawna odpowiedź z wyjaśnieniem i podstawą prawną.
CREATE TABLE questions (
    id               BIGSERIAL PRIMARY KEY,
    legal_act_id     BIGINT       NOT NULL REFERENCES legal_acts (id),
    source_id        BIGINT       NOT NULL REFERENCES question_sources (id),
    body             TEXT         NOT NULL,
    option_a         TEXT         NOT NULL,
    option_b         TEXT         NOT NULL,
    option_c         TEXT         NOT NULL,
    correct_option   VARCHAR(1)   NOT NULL,
    explanation      TEXT,
    legal_basis      VARCHAR(500),
    valid_as_of      DATE,
    -- Ukryte pytanie nie pojawia się nikomu (testy, fiszki, liczniki, egzamin); historia odpowiedzi zostaje.
    hidden           BOOLEAN      NOT NULL DEFAULT false,
    -- Ukryta fiszka: pytanie zostaje w testach, ale nie ma z niego fiszki (np. nie nadaje się do zapamiętywania).
    flashcard_hidden BOOLEAN      NOT NULL DEFAULT false,
    created_at       TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT chk_questions_correct_option CHECK (correct_option IN ('A', 'B', 'C')),
    CONSTRAINT chk_questions_body_not_blank CHECK (length(btrim(body)) > 0),
    CONSTRAINT chk_questions_legal_basis_not_blank CHECK (legal_basis IS NULL OR length(btrim(legal_basis)) > 0)
);
CREATE INDEX idx_questions_act ON questions (legal_act_id);
CREATE INDEX idx_questions_source ON questions (source_id);
-- Ochrona przed podwójnym wczytaniem tego samego arkusza (to samo pytanie w innym arkuszu jest dozwolone).
CREATE UNIQUE INDEX uq_questions_source_body ON questions (source_id, md5(body));

-- ======================================================================================================
-- Ustawienia i wybór użytkownika
-- ======================================================================================================

CREATE TABLE user_settings (
    user_id                    BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    -- Wybrana ścieżka (aplikacja albo Student). NULL: użytkownik jeszcze nie wybrał.
    track_id                   BIGINT REFERENCES tracks (id),
    -- Filtr źródeł pytań w Testach: własne arkusze / arkusze innych aplikacji / pytania autorskie.
    include_own_sheets         BOOLEAN          NOT NULL DEFAULT true,
    include_other_sheets       BOOLEAN          NOT NULL DEFAULT true,
    include_authored           BOOLEAN          NOT NULL DEFAULT true,
    new_flashcards_per_day     INT,
    new_test_questions_per_day INT,
    max_reviews_per_day        INT,
    desired_retention          DOUBLE PRECISION,
    shuffle_options            BOOLEAN          NOT NULL DEFAULT true,
    theme                      VARCHAR(10)      NOT NULL DEFAULT 'DARK',
    hint_hidden_test           BOOLEAN          NOT NULL DEFAULT false,
    hint_hidden_flashcard      BOOLEAN          NOT NULL DEFAULT false,
    question_timer_hidden      BOOLEAN          NOT NULL DEFAULT false,
    updated_at                 TIMESTAMP        NOT NULL DEFAULT now(),
    CONSTRAINT chk_user_settings_sources CHECK (include_own_sheets OR include_other_sheets OR include_authored),
    CONSTRAINT chk_user_settings_new_flashcards CHECK (new_flashcards_per_day IS NULL OR (new_flashcards_per_day BETWEEN 0 AND 200)),
    CONSTRAINT chk_user_settings_new_tests CHECK (new_test_questions_per_day IS NULL OR (new_test_questions_per_day BETWEEN 0 AND 500)),
    CONSTRAINT chk_user_settings_reviews CHECK (max_reviews_per_day IS NULL OR (max_reviews_per_day BETWEEN 10 AND 1000)),
    CONSTRAINT chk_user_settings_retention CHECK (desired_retention IS NULL OR (desired_retention BETWEEN 0.70 AND 0.97)),
    CONSTRAINT chk_user_settings_theme CHECK (theme IN ('DARK', 'LIGHT'))
);

-- Zawężenie puli do wybranych aktów prawnych (trwałe, brak wpisów = cała pula ścieżki).
CREATE TABLE user_pool_acts (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    legal_act_id BIGINT NOT NULL REFERENCES legal_acts (id) ON DELETE CASCADE,
    CONSTRAINT uq_user_pool_acts UNIQUE (user_id, legal_act_id)
);
CREATE INDEX idx_user_pool_acts_act ON user_pool_acts (legal_act_id);

-- Termin najbliższego egzaminu ścieżki-aplikacji (ustawia administrator). NULL: termin nieogłoszony.
CREATE TABLE exam_terms (
    track_id   BIGINT    PRIMARY KEY REFERENCES tracks (id) ON DELETE CASCADE,
    exam_date  DATE,
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

-- ======================================================================================================
-- Nauka: stan FSRS per pytanie i tryb, historia odpowiedzi, fiszki dodane ręcznie
-- ======================================================================================================

-- Test i fiszka tego samego pytania mają osobny stan i harmonogram (osobny wskaźnik FSRS).
CREATE TABLE user_question_states (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    question_id     BIGINT           NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    mode            VARCHAR(10)      NOT NULL,
    stability       DOUBLE PRECISION NOT NULL DEFAULT 0,
    difficulty      DOUBLE PRECISION NOT NULL DEFAULT 0,
    due_date        TIMESTAMP        NOT NULL DEFAULT now(),
    reps            INT              NOT NULL DEFAULT 0,
    lapses          INT              NOT NULL DEFAULT 0,
    state           VARCHAR(20)      NOT NULL DEFAULT 'NEW',
    last_review_at  TIMESTAMP,
    avg_time_seconds DOUBLE PRECISION,
    suspended       BOOLEAN          NOT NULL DEFAULT false,
    suspended_at    TIMESTAMP,
    version         BIGINT           NOT NULL DEFAULT 0,
    CONSTRAINT uq_user_question_states UNIQUE (user_id, question_id, mode),
    CONSTRAINT chk_user_question_states_mode CHECK (mode IN ('FLASHCARD', 'TEST')),
    CONSTRAINT chk_user_question_states_state CHECK (state IN ('NEW', 'LEARNING', 'REVIEW', 'RELEARNING')),
    CONSTRAINT chk_user_question_states_suspended_at CHECK (
        (suspended AND suspended_at IS NOT NULL) OR ((NOT suspended) AND suspended_at IS NULL))
);
CREATE INDEX idx_user_question_states_user_mode_due ON user_question_states (user_id, mode, due_date);
CREATE INDEX idx_user_question_states_question ON user_question_states (question_id);
CREATE INDEX idx_user_question_states_suspended ON user_question_states (user_id) WHERE suspended;

CREATE TABLE review_logs (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    question_id        BIGINT           NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    mode               VARCHAR(10)      NOT NULL DEFAULT 'FLASHCARD',
    rating             INT              NOT NULL,
    reviewed_at        TIMESTAMP        NOT NULL DEFAULT now(),
    elapsed_days       DOUBLE PRECISION,
    stability_before   DOUBLE PRECISION,
    stability_after    DOUBLE PRECISION,
    time_spent_seconds DOUBLE PRECISION,
    was_correct        BOOLEAN,
    was_guessed        BOOLEAN          NOT NULL DEFAULT false,
    CONSTRAINT chk_review_logs_mode CHECK (mode IN ('FLASHCARD', 'TEST')),
    CONSTRAINT chk_review_logs_rating CHECK (rating BETWEEN 1 AND 4)
);
CREATE INDEX idx_review_logs_user_reviewed_at ON review_logs (user_id, reviewed_at);
CREATE INDEX idx_review_logs_question ON review_logs (question_id);
CREATE INDEX idx_review_logs_first_reviews ON review_logs (user_id, mode, reviewed_at) WHERE elapsed_days IS NULL;

-- Ręczne „Dodaj do fiszek" przy pytaniu w teście: odblokowuje fiszkę tak samo jak pewny błąd.
CREATE TABLE flashcard_additions (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    question_id BIGINT    NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    added_at    TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_flashcard_additions UNIQUE (user_id, question_id)
);
CREATE INDEX idx_flashcard_additions_question ON flashcard_additions (question_id);

-- ======================================================================================================
-- Zgłoszenia, własne testy, egzaminy próbne, czas nauki
-- ======================================================================================================

CREATE TABLE question_reports (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    question_id BIGINT      NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    reason      VARCHAR(20) NOT NULL,
    comment     TEXT,
    status      VARCHAR(20) NOT NULL DEFAULT 'NEW',
    created_at  TIMESTAMP   NOT NULL DEFAULT now(),
    resolved_at TIMESTAMP,
    CONSTRAINT chk_question_reports_reason CHECK (reason IN ('WRONG_ANSWER', 'OUTDATED', 'UNCLEAR', 'OTHER')),
    CONSTRAINT chk_question_reports_status CHECK (status IN ('NEW', 'RESOLVED', 'REJECTED')),
    CONSTRAINT chk_question_reports_comment_length CHECK (comment IS NULL OR char_length(comment) <= 1000)
);
CREATE INDEX idx_question_reports_question ON question_reports (question_id);
CREATE INDEX idx_question_reports_status_created ON question_reports (status, created_at);
CREATE INDEX idx_question_reports_user_created ON question_reports (user_id, created_at DESC);
-- Jedno otwarte zgłoszenie danego pytania od danego użytkownika.
CREATE UNIQUE INDEX uq_question_reports_open_per_user_question ON question_reports (user_id, question_id) WHERE status = 'NEW';

CREATE TABLE custom_test_sessions (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    started_at      TIMESTAMP     NOT NULL,
    finished_at     TIMESTAMP,
    kind            VARCHAR(10)   NOT NULL,
    scope_label     VARCHAR(300)  NOT NULL,
    -- Numery wybranych dziedzin i aktów prawnych oddzielone przecinkami (puste: bez zawężenia).
    field_ids       VARCHAR(2000) NOT NULL DEFAULT '',
    act_ids         VARCHAR(2000) NOT NULL DEFAULT '',
    -- Źródła pytań w chwili układania testu: OWN_SHEETS, OTHER_SHEETS, AUTHORED oddzielone przecinkami albo NULL (wszystkie).
    source_groups   VARCHAR(100),
    requested_count INT           NOT NULL,
    available_count INT           NOT NULL,
    CONSTRAINT chk_custom_test_sessions_kind CHECK (kind IN ('ALL', 'NEW', 'MISTAKES', 'DUE')),
    CONSTRAINT chk_custom_test_sessions_counts CHECK (requested_count BETWEEN 1 AND 150 AND available_count >= 1)
);
CREATE INDEX idx_custom_test_sessions_user_started ON custom_test_sessions (user_id, started_at DESC);

CREATE TABLE custom_test_session_questions (
    id                 BIGSERIAL PRIMARY KEY,
    session_id         BIGINT           NOT NULL REFERENCES custom_test_sessions (id) ON DELETE CASCADE,
    question_id        BIGINT           NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    "position"         INT              NOT NULL,
    selected_option    VARCHAR(1),
    was_correct        BOOLEAN,
    was_guessed        BOOLEAN          NOT NULL DEFAULT false,
    time_spent_seconds DOUBLE PRECISION,
    answered_at        TIMESTAMP,
    CONSTRAINT uq_custom_test_session_questions_question UNIQUE (session_id, question_id),
    CONSTRAINT uq_custom_test_session_questions_position UNIQUE (session_id, "position"),
    CONSTRAINT chk_custom_test_session_questions_option CHECK (selected_option IS NULL OR selected_option IN ('A', 'B', 'C')),
    CONSTRAINT chk_custom_test_session_questions_answer CHECK (
        (answered_at IS NULL) = (selected_option IS NULL) AND (answered_at IS NULL) = (was_correct IS NULL))
);
CREATE INDEX idx_custom_test_session_questions_question ON custom_test_session_questions (question_id);

CREATE TABLE exam_attempts (
    id                     BIGSERIAL PRIMARY KEY,
    user_id                BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    finished_at            TIMESTAMP    NOT NULL,
    scope_label            VARCHAR(300) NOT NULL,
    question_count         INT          NOT NULL,
    answered_count         INT          NOT NULL,
    correct_count          INT          NOT NULL,
    pass_threshold_percent INT          NOT NULL,
    pass_points            INT,
    passed                 BOOLEAN      NOT NULL,
    time_limit_minutes     INT,
    duration_seconds       INT          NOT NULL,
    timed_out              BOOLEAN      NOT NULL DEFAULT false,
    CONSTRAINT chk_exam_attempts_counts CHECK (
        question_count BETWEEN 1 AND 200 AND answered_count BETWEEN 0 AND question_count
        AND correct_count BETWEEN 0 AND answered_count),
    CONSTRAINT chk_exam_attempts_threshold CHECK (pass_threshold_percent BETWEEN 1 AND 100),
    CONSTRAINT chk_exam_attempts_pass_points CHECK (pass_points IS NULL OR (pass_points >= 1 AND pass_points <= question_count)),
    CONSTRAINT chk_exam_attempts_time_limit CHECK (time_limit_minutes IS NULL OR time_limit_minutes BETWEEN 1 AND 600),
    CONSTRAINT chk_exam_attempts_duration CHECK (duration_seconds BETWEEN 0 AND 172800)
);
CREATE INDEX idx_exam_attempts_user_finished ON exam_attempts (user_id, finished_at DESC);

CREATE TABLE study_time_daily (
    user_id BIGINT  NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    day     DATE    NOT NULL,
    seconds INT     NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, day),
    CONSTRAINT chk_study_time_daily_seconds CHECK (seconds BETWEEN 0 AND 86400)
);
