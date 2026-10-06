CREATE TABLE users (
                       id BIGSERIAL PRIMARY KEY,
                       private_email VARCHAR(255) UNIQUE NOT NULL,
                       university_email VARCHAR(255) UNIQUE NOT NULL,
                       password_hash VARCHAR(255) NOT NULL,
                       is_university_email_verified BOOLEAN NOT NULL DEFAULT false,
                       is_active BOOLEAN NOT NULL DEFAULT false,
                       created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE email_verification_tokens (
                                           id BIGSERIAL PRIMARY KEY,
                                           user_id BIGINT NOT NULL REFERENCES users(id),
                                           token VARCHAR(255) UNIQUE NOT NULL,
                                           expires_at TIMESTAMP NOT NULL,
                                           used_at TIMESTAMP,
                                           created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE subjects (
                          id BIGSERIAL PRIMARY KEY,
                          name VARCHAR(255) NOT NULL,
                          slug VARCHAR(255) UNIQUE NOT NULL
);

CREATE TABLE chapters (
                          id BIGSERIAL PRIMARY KEY,
                          subject_id BIGINT NOT NULL REFERENCES subjects(id),
                          parent_chapter_id BIGINT REFERENCES chapters(id),
                          title VARCHAR(500) NOT NULL,
                          order_index INT NOT NULL
);

CREATE TABLE sources (
                         id BIGSERIAL PRIMARY KEY,
                         name VARCHAR(255) NOT NULL,
                         author VARCHAR(255),
                         edition VARCHAR(100)
);

CREATE TABLE cards (
                       id BIGSERIAL PRIMARY KEY,
                       chapter_id BIGINT NOT NULL REFERENCES chapters(id),
                       source_id BIGINT REFERENCES sources(id),
                       type VARCHAR(50) NOT NULL,
                       front TEXT NOT NULL,
                       back TEXT NOT NULL,
                       page_number INT,
                       is_verbatim_quote BOOLEAN NOT NULL DEFAULT false,
                       created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE tags (
                      id BIGSERIAL PRIMARY KEY,
                      name VARCHAR(255) UNIQUE NOT NULL
);

CREATE TABLE card_tags (
                           card_id BIGINT NOT NULL REFERENCES cards(id),
                           tag_id BIGINT NOT NULL REFERENCES tags(id),
                           PRIMARY KEY (card_id, tag_id)
);

CREATE TABLE user_chapter_progress (
                                       user_id BIGINT NOT NULL REFERENCES users(id),
                                       chapter_id BIGINT NOT NULL REFERENCES chapters(id),
                                       is_read BOOLEAN NOT NULL DEFAULT false,
                                       read_at TIMESTAMP,
                                       PRIMARY KEY (user_id, chapter_id)
);

CREATE TABLE user_card_state (
                                 user_id BIGINT NOT NULL REFERENCES users(id),
                                 card_id BIGINT NOT NULL REFERENCES cards(id),
                                 stability DOUBLE PRECISION NOT NULL DEFAULT 0,
                                 difficulty DOUBLE PRECISION NOT NULL DEFAULT 0,
                                 due_date TIMESTAMP NOT NULL DEFAULT now(),
                                 reps INT NOT NULL DEFAULT 0,
                                 lapses INT NOT NULL DEFAULT 0,
                                 state VARCHAR(20) NOT NULL DEFAULT 'new',
                                 last_review_at TIMESTAMP,
                                 avg_time_seconds DOUBLE PRECISION,
                                 PRIMARY KEY (user_id, card_id)
);

CREATE TABLE review_logs (
                             id BIGSERIAL PRIMARY KEY,
                             user_id BIGINT NOT NULL REFERENCES users(id),
                             card_id BIGINT NOT NULL REFERENCES cards(id),
                             rating INT NOT NULL,
                             reviewed_at TIMESTAMP NOT NULL DEFAULT now(),
                             elapsed_days DOUBLE PRECISION,
                             stability_before DOUBLE PRECISION,
                             stability_after DOUBLE PRECISION,
                             time_spent_seconds DOUBLE PRECISION
);